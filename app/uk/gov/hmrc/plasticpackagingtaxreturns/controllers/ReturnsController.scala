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
import play.api.libs.json.{JsObject, JsPath}
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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, FinancialDataService, PPTCalculationService, PPTFinancialsService}
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
  calculationsService: PPTCalculationService,
  financialDataService: FinancialDataService,
  financialsService: PPTFinancialsService,
  creditsService: CreditsCalculationService,
  availableCreditService: AvailableCreditService
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

  def amend(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      getUserAnswer(request)(userAnswer =>  {
        isDirectDebitInProgress(pptReference, userAnswer.get[String](JsPath \ "amendSelectedPeriodKey").getOrElse(throw new Exception("Cannot amend return without period Key")))
          .flatMap( isDDInProgress =>
            if(isDDInProgress)
              Future.successful(UnprocessableEntity("Could not finish transaction as Direct Debit is in progress."))
            else doSubmit(pptReference, AmendReturnValues.apply, userAnswer)
        )
      })
    }

  def submit(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      getUserAnswer(request) { userAnswer =>
        availableCreditService.getBalance(userAnswer).flatMap{ availableCredit =>
          val requestedCredits = creditsService.totalRequestedCredit(userAnswer)
          doSubmit(pptReference, NewReturnValues.apply(requestedCredits, availableCredit), userAnswer)
        }
      }
    }

  private def getUserAnswer(request: AuthorizedRequest[AnyContent])(fun: UserAnswers => Future[Result]): Future[Result] = {
    sessionRepository.get(request.cacheKey).flatMap {
      _.fold(Future.successful(UnprocessableEntity("UserAnswer is empty")))(fun(_))
    }
  }

  private def doSubmit(
    pptReference: String,
    getValuesOutOfUserAnswers: UserAnswers => Option[ReturnValues],
    userAnswer: UserAnswers
  )(implicit request: AuthorizedRequest[AnyContent]) : Future[Result] = {

    getValuesOutOfUserAnswers(userAnswer).map(_ -> userAnswer)
      .fold {
        Future.successful(UnprocessableEntity("Unable to build ReturnsSubmissionRequest from UserAnswers"))
      } { case (returnValues, userAnswers) =>

        submitReturnWithNrs(pptReference, userAnswers, returnValues)
      }
  }

  private def isDirectDebitInProgress(
    pptReference: String,
    periodKey: String
  ) (implicit request: AuthorizedRequest[AnyContent]): Future[Boolean] = {
          financialDataService.getFinancials(pptReference, request.internalId).map {
            case Left(status) => throw new RuntimeException(s"Could not get Direct Debit details. Server responded with status code: $status")
            case Right(financialDataResponse) =>
              financialsService.lookUpForDdInProgress(periodKey, financialDataResponse)
          }
  }

  private def submitReturnWithNrs
  (
    pptReference: String,
   userAnswers: UserAnswers,
   returnValues: ReturnValues
  )(implicit request: AuthorizedRequest[AnyContent]): Future[Result] = {
    val calculations: Calculations = calculationsService.calculate(returnValues)

    if (calculations.isSubmittable) {
      val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calculations)
      returnsConnector.submitReturn(pptReference, eisRequest, request.internalId).flatMap {
        case Right(response) =>
          sessionRepository.clear(request.cacheKey).andThen {
            case Success(_) => logger.info(s"Successfully removed tax return for $pptReference from cache")
            case Failure(ex) => logger.error(s"Failed to remove tax return for $pptReference from cache- ${ex.getMessage}", ex)
          }
          handleNrsRequest(request, userAnswers.data, eisRequest, response)
        case Left(errorStatusCode) => Future.successful(new Status(errorStatusCode))
      }
    } else
      Future.successful(UnprocessableEntity("The calculation is not submittable"))
  }

  private def handleNrsRequest(request: AuthorizedRequest[AnyContent],
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
