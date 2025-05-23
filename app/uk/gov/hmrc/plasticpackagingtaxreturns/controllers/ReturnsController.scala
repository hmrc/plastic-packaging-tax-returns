/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.NrsSubmitReturnEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector.StatusCode
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.{ObligationsDataConnector, ReturnsConnector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ReturnsController.ReturnWithTaxRate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.PeriodKeyGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.NotableEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{
  AvailableCreditService,
  CreditsCalculationService,
  FinancialDataService,
  PPTCalculationService,
  PPTFinancialsService
}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, TaxRateTable}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsController @Inject() (
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  nonRepudiationService: NonRepudiationService,
  override val controllerComponents: ControllerComponents,
  returnsConnector: ReturnsConnector,
  obligationsDataConnector: ObligationsDataConnector,
  appConfig: AppConfig,
  auditConnector: AuditConnector,
  calculationsService: PPTCalculationService,
  financialDataService: FinancialDataService,
  financialsService: PPTFinancialsService,
  creditsService: CreditsCalculationService,
  availableCreditService: AvailableCreditService,
  taxRateTable: TaxRateTable,
  edgeOfSystem: EdgeOfSystem
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses with Logging {

  private def parseDate(date: String): ZonedDateTime = {
    val df = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))
    ZonedDateTime.parse(date, df)
  }

  def get(pptReference: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      returnsConnector.get(pptReference = pptReference, periodKey = periodKey, internalId = request.internalId).map {
        case Right(displayReturnJson) =>
          val endDate = (displayReturnJson \ "chargeDetails" \ "periodTo").get.as[LocalDate]
          val taxRate = taxRateTable.lookupRateFor(endDate)
          
          val returnWithTaxRate = ReturnWithTaxRate(displayReturnJson, taxRate)
          Ok(returnWithTaxRate)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }
    }

  def amend(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      getUserAnswer(request) { userAnswer =>
        if (isReturnTooOldToAmend(userAnswer))
          Future.successful(UnprocessableEntity("Can not amend this return. Amendable period is closed."))
        else
          isDirectDebitInProgress(
            pptReference,
            userAnswer.get[String](JsPath \ "amendSelectedPeriodKey")
              .getOrElse(throw new Exception("Cannot amend return without period Key"))
          )
            .flatMap(isDDInProgress =>
              if (isDDInProgress)
                Future.successful(UnprocessableEntity("Could not finish transaction as Direct Debit is in progress."))
              else
                doSubmit(NotableEvent.PptReturn, pptReference, AmendReturnValues.apply, userAnswer)
            )
      }
    }

  def submit(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      getUserAnswer(request) { userAnswer =>
        isPeriodStillOpen(pptReference, userAnswer).flatMap { periodIsOpen =>
          if (periodIsOpen)
            availableCreditService.getBalance(userAnswer).flatMap { availableCredit =>
              val requestedCredits: BigDecimal =
                creditsService.totalRequestedCredit(userAnswer, availableCredit).totalRequestedCreditInPounds
              doSubmit(
                NotableEvent.PptReturn,
                pptReference,
                NewReturnValues.apply(requestedCredits, availableCredit),
                userAnswer
              )
            }
          else {
            sessionRepository.clearUserAnswers(pptReference, request.cacheKey)
            Future.successful(
              ExpectationFailed("Obligation period is not open. Maybe already submitted or yet to be open.")
            )
          }
        }
      }
    }

  private def getUserAnswer(request: AuthorizedRequest[AnyContent])(fun: UserAnswers => Future[Result])
    : Future[Result] =
    sessionRepository.get(request.cacheKey).flatMap {
      _.fold(Future.successful(UnprocessableEntity("UserAnswer is empty")))(fun(_))
    }

  private def doSubmit(
    nrsEventType: NotableEvent,
    pptReference: String,
    getValuesOutOfUserAnswers: UserAnswers => Option[ReturnValues],
    userAnswer: UserAnswers
  )(implicit request: AuthorizedRequest[AnyContent]): Future[Result] =
    getValuesOutOfUserAnswers(userAnswer)
      .fold {
        Future.successful(UnprocessableEntity("Unable to build ReturnsSubmissionRequest from UserAnswers"))
      } { returnValues =>
        submitReturnWithNrs(nrsEventType, pptReference, userAnswer, returnValues)
      }

  private def isPeriodStillOpen(pptReference: String, userAnswer: UserAnswers)(implicit
    request: AuthorizedRequest[AnyContent]
  ): Future[Boolean] = {
    val periodKey = userAnswer.getOrFail(PeriodKeyGettable)
    obligationsDataConnector.get(pptReference, request.internalId, None, None, Some(ObligationStatus.OPEN)).map {
      _.fold(
        status =>
          throw new RuntimeException(
            s"Could not get Open Obligation details. Server responded with status code: $status"
          ),
        _.obligations.flatMap(_.obligationDetails.map(_.periodKey)).contains(periodKey)
      )
    }
  }

  private def isDirectDebitInProgress(pptReference: String, periodKey: String)(implicit
    request: AuthorizedRequest[AnyContent]
  ): Future[Boolean] =
    financialDataService.getFinancials(pptReference, request.internalId).map {
      case Left(status) =>
        throw new RuntimeException(s"Could not get Direct Debit details. Server responded with status code: $status")
      case Right(financialDataResponse) =>
        financialsService.lookUpForDdInProgress(periodKey, financialDataResponse)
    }

  private def isReturnTooOldToAmend(userAnswer: UserAnswers): Boolean = {
    val dueDate = userAnswer.getOrFail[LocalDate](JsPath \ "amend" \ "obligation" \ "dueDate")
    val today   = edgeOfSystem.localDateTimeNow.toLocalDate
    dueDate.isBefore(today.minusYears(4))
  }

  private def submitReturnWithNrs(
    nrsEventType: NotableEvent,
    pptReference: String,
    userAnswers: UserAnswers,
    returnValues: ReturnValues
  )(implicit request: AuthorizedRequest[AnyContent]): Future[Result] = {
    val calculations: Calculations = calculationsService.calculate(returnValues)

    if (calculations.isSubmittable) {
      val eisRequest: ReturnsSubmissionRequest = ReturnsSubmissionRequest(returnValues, calculations)
      returnsConnector.submitReturn(pptReference, eisRequest, request.internalId).flatMap {
        case Right(response) =>
          sessionRepository.clearUserAnswers(pptReference, request.cacheKey)
          handleNrsRequest(nrsEventType, request, userAnswers.data, eisRequest, response)

        case Left(StatusCode.RETURN_ALREADY_SUBMITTED) =>
          Future.successful {
            new Status(StatusCode.RETURN_ALREADY_SUBMITTED)(Json.obj("returnAlreadyReceived" -> returnValues.periodKey))
          }

        case Left(errorStatusCode) => Future.successful(new Status(errorStatusCode))
      }
    } else
      Future.successful(UnprocessableEntity("The calculation is not submittable"))
  }

  private def handleNrsRequest(
    nrsEventType: NotableEvent,
    request: AuthorizedRequest[AnyContent],
    userAnswers: JsObject,
    returnSubmissionRequest: ReturnsSubmissionRequest,
    eisResponse: Return
  )(implicit hc: HeaderCarrier): Future[Result] = {

    val payload = NrsReturnOrAmendSubmission(userAnswers, returnSubmissionRequest)

    submitToNrs(nrsEventType, request, payload, eisResponse).map {
      case NonRepudiationSubmissionAccepted(nrSubmissionId) =>
        auditConnector.sendExplicitAudit(
          NrsSubmitReturnEvent.eventType,
          NrsSubmitReturnEvent(
            updateNrsDetails(
              nrsSubmissionId = Some(nrSubmissionId),
              returnSubmissionRequest = returnSubmissionRequest,
              nrsFailureResponse = None
            ),
            pptReference = Some(request.pptReference),
            processingDateTime = Some(parseDate(eisResponse.processingDate))
          )
        )

        handleNrsSuccess(eisResponse, nrSubmissionId)

    }.recoverWith {
      case exception: Exception =>
        auditConnector.sendExplicitAudit(
          NrsSubmitReturnEvent.eventType,
          NrsSubmitReturnEvent(
            updateNrsDetails(
              nrsSubmissionId = None,
              returnSubmissionRequest = returnSubmissionRequest,
              nrsFailureResponse = Some(exception.getMessage)
            ),
            pptReference = Some(request.pptReference),
            processingDateTime = Some(parseDate(eisResponse.processingDate))
          )
        )

        handleNrsFailure(eisResponse, exception)

    }
  }

  private def submitToNrs(
    nrsEventType: NotableEvent,
    request: AuthorizedRequest[AnyContent],
    payload: NrsReturnOrAmendSubmission,
    eisResponse: Return
  )(implicit hc: HeaderCarrier): Future[NonRepudiationSubmissionAccepted] = {

    logger.debug(
      s"Submitting NRS payload: ${toJson(payload)} with request headers: ${toJson(request.headers.headers)} for PPT Ref: ${eisResponse.idDetails.pptReferenceNumber}"
    )

    nonRepudiationService.submitNonRepudiation(
      nrsEventType,
      toJson(payload).toString,
      parseDate(eisResponse.processingDate),
      eisResponse.idDetails.pptReferenceNumber,
      request.headers.headers.toMap
    )
  }

  private def handleNrsFailure(eisResponse: Return, exception: Exception): Future[Result] = {

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
      Some(NrsDetails(nrsSubmissionId = nrsSubmissionId, failureReason = nrsFailureResponse))
    )

}

object ReturnsController {

  case class ReturnWithTaxRate(displayReturnJson: JsValue, taxRate: BigDecimal)

  object ReturnWithTaxRate {
    implicit val format: Writes[ReturnWithTaxRate] = Json.writes[ReturnWithTaxRate]
  }

}
