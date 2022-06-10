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
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.Auditor
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnWithNrsFailureResponse, ReturnWithNrsSuccessResponse, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.ZonedDateTime
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

  def submit(pptReference: String): Action[TaxReturn] = doSubmission(pptReference, None)

  def amend(pptReference: String, submissionId: String): Action[TaxReturn] = doSubmission(pptReference, Some(submissionId))

  def get(pptReference: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      returnsConnector.get(pptReference = pptReference, periodKey = periodKey).map {
        case Right(response) => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }

    }

  private def doSubmission(pptReference: String, submissionId: Option[String]): Action[TaxReturn] =
    authenticator.authorisedAction(authenticator.parsingJson[TaxReturn], pptReference) { implicit request =>
      val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(request.body, appConfig.taxRatePoundsPerKg, submissionId = submissionId)
      returnsConnector.submitReturn(pptReference, eisRequest).flatMap {
        case Right(response) =>
          sessionRepository.clear(request.internalId).andThen {
            case Success(_) => logger.info(s"Successfully removed tax return for $pptReference from cache")
            case Failure(ex) => logger.warn(s"Failed to remove tax return for $pptReference from cache- ${ex.getMessage}", ex)
          }
          handleNrsRequest(request, eisRequest, response)
        case Left(errorStatusCode) => Future.successful(new Status(errorStatusCode))
      }
    }

  private def handleNrsRequest(
                                request: AuthorizedRequest[TaxReturn],
                                returnSubmissionRequest: ReturnsSubmissionRequest,
                                eisResponse: Return
                              )(implicit hc: HeaderCarrier): Future[Result] = {

    // TODO - implement this if we need to...
    // NOTE - If we want to audit the user answers as well as EIS submission payload we can get from the cache via;
    val usrAnswersJson = sessionRepository.get(request.internalId).map { ans =>
      ans.getOrElse(UserAnswers(request.internalId)).data
    }
    // We could then combine this json with the eis payload and audit everything.

    submitToNrs(request, returnSubmissionRequest, eisResponse).map {
      case NonRepudiationSubmissionAccepted(nrSubmissionId) =>
        val dateTime = ZonedDateTime.parse(eisResponse.processingDate)

        auditor.returnSubmitted(
          updateNrsDetails(nrsSubmissionId = Some(nrSubmissionId),
            returnSubmissionRequest = returnSubmissionRequest,
            nrsFailureResponse = None
          ),
          pptReference = Some(request.pptId),
          processingDateTime = Some(dateTime)
        )

        handleNrsSuccess(eisResponse, nrSubmissionId)

    }.recoverWith {
      case exception: Exception =>
        val dateTime = ZonedDateTime.parse(eisResponse.processingDate)

        auditor.returnSubmitted(
          updateNrsDetails(nrsSubmissionId = None,
            returnSubmissionRequest = returnSubmissionRequest,
            nrsFailureResponse = Some(exception.getMessage)
          ),
          pptReference = Some(request.pptId),
          processingDateTime = Some(dateTime)
        )

        handleNrsFailure(eisResponse, exception)

    }
  }

  private def submitToNrs(
                           request: AuthorizedRequest[TaxReturn],
                           returnSubmissionRequest: ReturnsSubmissionRequest,
                           eisResponse: Return
                         )(implicit hc: HeaderCarrier): Future[NonRepudiationSubmissionAccepted] = {

    val dateTime = ZonedDateTime.parse(eisResponse.processingDate)

    nonRepudiationService.submitNonRepudiation(toJson(returnSubmissionRequest).toString,
      dateTime,
      eisResponse.idDetails.pptReferenceNumber,
      request.headers.headers.toMap // TODO - What are the correct headers? All headers or just PPT header?
    )
  }

  private def handleNrsFailure(
                                eisResponse: Return,
                                exception: Exception
                              ): Future[Result] =
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

  private def handleNrsSuccess(eisResponse: Return, nrSubmissionId: String): Result =
    Ok(
      ReturnWithNrsSuccessResponse(
        eisResponse.processingDate,
        eisResponse.idDetails,
        eisResponse.chargeDetails,
        eisResponse.exportChargeDetails,
        eisResponse.returnDetails,
        nrSubmissionId)
    )

  private def updateNrsDetails(
                                nrsSubmissionId: Option[String],
                                nrsFailureResponse: Option[String],
                                returnSubmissionRequest: ReturnsSubmissionRequest
                              ): ReturnsSubmissionRequest =
    returnSubmissionRequest.copy(nrsDetails =
      Some(NrsDetails(nrsSubmissionId = nrsSubmissionId, failureReason = nrsFailureResponse)))

}
