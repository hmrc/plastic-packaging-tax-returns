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
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json.{reads, toJson}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.Auditor
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{NrsReturnOrAmendSubmission, Return, ReturnWithNrsFailureResponse, ReturnWithNrsSuccessResponse, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId, ZoneOffset, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReturnsSubmissionController @Inject()(
                                             authenticator: Authenticator,
                                             sessionRepository: SessionRepository,
                                             nonRepudiationService: NonRepudiationService,
                                             override val controllerComponents: ControllerComponents,
                                             returnsConnector: ReturnsConnector,
                                             appConfig: AppConfig,
                                             auditor: Auditor,
                                           )(implicit executionContext: ExecutionContext)
  extends BackendController(controllerComponents) with JSONResponses {

  private val logger = Logger(this.getClass)

  def parseDate(date: String): ZonedDateTime = {
    val df = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))
    LocalDate.parse(date, df).atStartOfDay().atZone(ZoneOffset.UTC)
  }

  def submit(pptReference: String): Action[TaxReturn] = doSubmission(pptReference, None)

  def amend(pptReference: String, submissionId: String): Action[TaxReturn] = doSubmission(pptReference, Some(submissionId))

  def get(pptReference: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      returnsConnector.get(pptReference = pptReference, periodKey = periodKey).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }

    }

  private def doSubmission(pptReference: String, submissionId: Option[String]): Action[TaxReturn] =
    authenticator.authorisedAction(authenticator.parsingJson[TaxReturn], pptReference) { implicit request =>
      val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(request.body, appConfig.taxRatePoundsPerKg, submissionId = submissionId)
      returnsConnector.submitReturn(pptReference, eisRequest).flatMap {
        case Right(response) =>
          userAnswers(request).flatMap { ans =>
            sessionRepository.clear(request.cacheKey).andThen {
              case Success(_)  => logger.info(s"Successfully removed tax return for $pptReference from cache")
              case Failure(ex) => logger.warn(s"Failed to remove tax return for $pptReference from cache- ${ex.getMessage}", ex)
            }
            handleNrsRequest(request, ans, eisRequest, response)
          }
        case Left(errorStatusCode) => Future.successful(new Status(errorStatusCode))
      }
    }

  private def userAnswers(request: AuthorizedRequest[TaxReturn]) = {
    sessionRepository.get(request.cacheKey).map { ans =>
      ans.getOrElse(UserAnswers(request.cacheKey)).data
    }
  }

  private def handleNrsRequest(
                                request: AuthorizedRequest[TaxReturn],
                                userAnswers: JsObject,
                                returnSubmissionRequest: ReturnsSubmissionRequest,
                                eisResponse: Return
                              )(implicit hc: HeaderCarrier): Future[Result] = {

    val payload = NrsReturnOrAmendSubmission(userAnswers, returnSubmissionRequest)

    submitToNrs(request, payload, eisResponse).map {
      case NonRepudiationSubmissionAccepted(nrSubmissionId) =>
        auditor.returnSubmitted(
          updateNrsDetails(nrsSubmissionId = Some(nrSubmissionId),
            returnSubmissionRequest = returnSubmissionRequest,
            nrsFailureResponse = None
          ),
          pptReference = Some(request.pptId),
          processingDateTime = Some(parseDate(eisResponse.processingDate))
        )

        handleNrsSuccess(eisResponse, nrSubmissionId)

    }.recoverWith {
      case exception: Exception =>
        auditor.returnSubmitted(
          updateNrsDetails(nrsSubmissionId = None,
            returnSubmissionRequest = returnSubmissionRequest,
            nrsFailureResponse = Some(exception.getMessage)
          ),
          pptReference = Some(request.pptId),
          processingDateTime = Some(parseDate(eisResponse.processingDate))
        )

        handleNrsFailure(eisResponse, exception)

    }
  }

  private def submitToNrs(
                           request: AuthorizedRequest[TaxReturn],
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

    logger.warn(s"NRS submission failed for: ${eisResponse.idDetails.pptReferenceNumber}")

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
