/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.JsObject
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.NrsSubmitReturnEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTCalculationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReturnsController @Inject()(
                                             authenticator: Authenticator,
                                             sessionRepository: SessionRepository,
                                             nonRepudiationService: NonRepudiationService,
                                             override val controllerComponents: ControllerComponents,
                                             returnsConnector: ReturnsConnector,
                                             appConfig: AppConfig,
                                             auditConnector: AuditConnector,
                                             calculationsService: PPTCalculationService
                                           )(implicit executionContext: ExecutionContext)
  extends BackendController(controllerComponents) with JSONResponses with Logging {

  private def parseDate(date: String): ZonedDateTime = {
    val df = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))
    ZonedDateTime.parse(date, df)
  }

  def get(pptReference: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      returnsConnector.get(pptReference = pptReference, periodKey = periodKey, internalId = request.internalId).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }

    }

  //todo submissionId is not needed here we have it in the cache, remove from FE and BE at the same time
  def amend(pptReference: String, submissionId: String): Action[AnyContent] = doSubmit(pptReference, AmendReturnValues.apply)

  def submit(pptReference: String): Action[AnyContent] = doSubmit(pptReference, NewReturnValues.apply)

  def doSubmit(pptReference: String, getValuesOutOfUserAnswers: UserAnswers => Option[ReturnValues]): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>

      sessionRepository.get(request.cacheKey).flatMap {
        _.flatMap(uA => getValuesOutOfUserAnswers(uA).map(_ -> uA))
          .fold {
            Future.successful(UnprocessableEntity("Unable to build ReturnsSubmissionRequest from UserAnswers"))
          } { case (returnValues, userAnswers) =>
            val calculations: Calculations = calculationsService.calculate(returnValues)

            if (calculations.isSubmittable) {
              val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calculations)
              returnsConnector.submitReturn(pptReference, eisRequest, request.internalId).flatMap {
                case Right(response) =>
                  sessionRepository.clear(request.cacheKey).andThen {
                    case Success(_)  => logger.info(s"Successfully removed tax return for $pptReference from cache")
                    case Failure(ex) => logger.error(s"Failed to remove tax return for $pptReference from cache- ${ex.getMessage}", ex)
                  }
                  handleNrsRequest(request, userAnswers.data, eisRequest, response)
                case Left(errorStatusCode) => Future.successful(new Status(errorStatusCode))
              }
            } else
              Future.successful(UnprocessableEntity("The calculation is not submittable"))
          }
      }

    }

  private def handleNrsRequest(
                                request: AuthorizedRequest[AnyContent],
                                userAnswers: JsObject,
                                returnSubmissionRequest: ReturnsSubmissionRequest,
                                eisResponse: Return
                              )(implicit hc: HeaderCarrier): Future[Result] = {

    val payload = NrsReturnOrAmendSubmission(userAnswers, returnSubmissionRequest)

    submitToNrs(request, payload, eisResponse).map {
      case NonRepudiationSubmissionAccepted(nrSubmissionId) =>

        auditConnector.sendExplicitAudit(
          NrsSubmitReturnEvent.eventType,
          NrsSubmitReturnEvent(
            updateNrsDetails(nrsSubmissionId = Some(nrSubmissionId),
              returnSubmissionRequest = returnSubmissionRequest,
              nrsFailureResponse = None
            ),
            pptReference = Some(request.pptId),
            processingDateTime = Some(parseDate(eisResponse.processingDate))
          )
        )

        handleNrsSuccess(eisResponse, nrSubmissionId)

    }.recoverWith {
      case exception: Exception =>
        auditConnector.sendExplicitAudit(
          NrsSubmitReturnEvent.eventType,
          NrsSubmitReturnEvent(
            updateNrsDetails(nrsSubmissionId = None,
              returnSubmissionRequest = returnSubmissionRequest,
              nrsFailureResponse = Some(exception.getMessage)
            ),
            pptReference = Some(request.pptId),
            processingDateTime = Some(parseDate(eisResponse.processingDate))
          )
        )

        handleNrsFailure(eisResponse, exception)

    }
  }

  private def submitToNrs(
                           request: AuthorizedRequest[AnyContent],
                           payload: NrsReturnOrAmendSubmission,
                           eisResponse: Return
                         )(implicit hc: HeaderCarrier): Future[NonRepudiationSubmissionAccepted] = {

    logger.debug(
      s"Submitting NRS payload: ${toJson(payload)} with request headers: ${toJson(request.headers.headers)} for PPT Ref: ${eisResponse.idDetails.pptReferenceNumber}"
    )

    nonRepudiationService.submitNonRepudiation(toJson(payload).toString,
      parseDate(eisResponse.processingDate),
      eisResponse.idDetails.pptReferenceNumber,
      request.headers.headers.toMap
    )
  }

  private def handleNrsFailure(
                                eisResponse: Return,
                                exception: Exception
                              ): Future[Result] = {

    logger.error(s"NRS submission failed for: ${eisResponse.idDetails.pptReferenceNumber}")

    Future.successful(
      Ok(
        ReturnWithNrsFailureResponse(
          eisResponse.processingDate,
          eisResponse.idDetails,
          eisResponse.chargeDetails,
          eisResponse.exportChargeDetails,
          eisResponse.returnDetails,
          exception.getMessage
        )
      )
    )
  }

  private def handleNrsSuccess(eisResponse: Return, nrSubmissionId: String): Result = {

    logger.info(s"NRS submission success for PPT Ref: ${eisResponse.idDetails.pptReferenceNumber}")

    Ok(
      ReturnWithNrsSuccessResponse(
        eisResponse.processingDate,
        eisResponse.idDetails,
        eisResponse.chargeDetails,
        eisResponse.exportChargeDetails,
        eisResponse.returnDetails,
        nrSubmissionId
      )
    )
  }

  private def updateNrsDetails(
                                nrsSubmissionId: Option[String],
                                nrsFailureResponse: Option[String],
                                returnSubmissionRequest: ReturnsSubmissionRequest
                              ): ReturnsSubmissionRequest =
    returnSubmissionRequest.copy(nrsDetails =
      Some(NrsDetails(nrsSubmissionId = nrsSubmissionId, failureReason = nrsFailureResponse)))

}
