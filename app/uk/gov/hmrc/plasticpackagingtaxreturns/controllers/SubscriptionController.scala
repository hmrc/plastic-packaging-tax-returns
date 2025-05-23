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

import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.ChangeSubscriptionEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{
  SubscriptionUpdateRequest,
  SubscriptionUpdateSuccessfulResponse,
  SubscriptionUpdateWithNrsFailureResponse,
  SubscriptionUpdateWithNrsSuccessfulResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationSubmissionAccepted, NrsDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.NotableEvent
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionController @Inject() (
  subscriptionsConnector: SubscriptionsConnector,
  authenticator: Authenticator,
  auditConnector: AuditConnector,
  nonRepudiationService: NonRepudiationService,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      subscriptionsConnector.getSubscription(pptReference).map {
        case Right(response) => Ok(response)
        case Left(errorResponse) =>
          val bodyAsJson = Json.parse(errorResponse.body)
          new Status(errorResponse.status)(bodyAsJson)
      }
    }

  def update(pptReference: String): Action[SubscriptionUpdateRequest] =
    authenticator.authorisedAction(authenticator.parsingJson[SubscriptionUpdateRequest], pptReference) {
      implicit request =>
        val updatedSubscription = request.body.toSubscription
        subscriptionsConnector.updateSubscription(pptReference, request.body).flatMap {
          case response @ SubscriptionUpdateSuccessfulResponse(_, _, _) =>
            handleNrsRequest(request, updatedSubscription, response)
        }
    }

  private def handleNrsRequest(
    request: AuthorizedRequest[SubscriptionUpdateRequest],
    pptSubscription: Subscription,
    eisResponse: SubscriptionUpdateSuccessfulResponse
  )(implicit hc: HeaderCarrier): Future[Result] =
    submitToNrs(request, pptSubscription, eisResponse).map {
      case NonRepudiationSubmissionAccepted(nrSubmissionId) =>
        auditConnector.sendExplicitAudit(
          ChangeSubscriptionEvent.eventType,
          ChangeSubscriptionEvent(
            updateNrsDetails(
              nrsSubmissionId = Some(nrSubmissionId),
              pptSubscription = pptSubscription,
              nrsFailureResponse = None
            ),
            pptReference = Some(eisResponse.pptReferenceNumber),
            processingDateTime = Some(eisResponse.processingDate)
          )
        )

        handleNrsSuccess(eisResponse, nrSubmissionId)

    }.recoverWith {
      case exception: Exception =>
        auditConnector.sendExplicitAudit(
          ChangeSubscriptionEvent.eventType,
          ChangeSubscriptionEvent(
            updateNrsDetails(
              nrsSubmissionId = None,
              pptSubscription = pptSubscription,
              nrsFailureResponse = Some(exception.getMessage)
            ),
            pptReference = Some(eisResponse.pptReferenceNumber),
            None
          )
        )

        handleNrsFailure(eisResponse, exception)

    }

  private def submitToNrs(
    request: AuthorizedRequest[SubscriptionUpdateRequest],
    pptSubscription: Subscription,
    eisResponse: SubscriptionUpdateSuccessfulResponse
  )(implicit hc: HeaderCarrier): Future[NonRepudiationSubmissionAccepted] =
    nonRepudiationService.submitNonRepudiation(
      NotableEvent.PptSubscription,
      toJson(pptSubscription).toString,
      eisResponse.processingDate,
      eisResponse.pptReferenceNumber,
      request.body.userHeaders.getOrElse(Map.empty)
    )

  private def handleNrsFailure(
    eisResponse: SubscriptionUpdateSuccessfulResponse,
    exception: Exception
  ): Future[Result] =
    Future.successful(
      Ok(
        SubscriptionUpdateWithNrsFailureResponse(
          eisResponse.pptReferenceNumber,
          eisResponse.processingDate,
          eisResponse.formBundleNumber,
          exception.getMessage
        )
      )
    )

  private def handleNrsSuccess(eisResponse: SubscriptionUpdateSuccessfulResponse, nrSubmissionId: String): Result =
    Ok(
      SubscriptionUpdateWithNrsSuccessfulResponse(
        eisResponse.pptReferenceNumber,
        eisResponse.processingDate,
        eisResponse.formBundleNumber,
        nrSubmissionId
      )
    )

  private def updateNrsDetails(
    nrsSubmissionId: Option[String],
    nrsFailureResponse: Option[String],
    pptSubscription: Subscription
  ): Subscription =
    pptSubscription.copy(nrsDetails =
      Some(NrsDetails(nrsSubmissionId = nrsSubmissionId, failureReason = nrsFailureResponse))
    )

}
