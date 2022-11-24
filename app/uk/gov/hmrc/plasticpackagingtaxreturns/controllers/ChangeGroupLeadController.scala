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

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.nonRepudiation.NrsSubscriptionUpdateSubmission
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.ChangeGroupLeadService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChangeGroupLeadController @Inject() (
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  cc: ControllerComponents,
  changeGroupLeadService: ChangeGroupLeadService,
  subscriptionsConnector: SubscriptionsConnector,
  nonRepudiationService: NonRepudiationService
) (implicit executionContext: ExecutionContext) extends BackendController(cc) with Logging {

  def change(pptReference: String): Action[AnyContent] = authenticator.authorisedAction(parse.default, pptReference) {
    implicit request: AuthorizedRequest[AnyContent] =>
      val pptReference = request.pptReference
      val getSubF: Future[Either[HttpResponse, SubscriptionDisplayResponse]] = subscriptionsConnector.getSubscription(pptReference)
      val getUAF: Future[Option[UserAnswers]] = sessionRepository.get(request.cacheKey)

      {
        for {
          subscriptionEither <- getSubF
          maybeUserAnswers <- getUAF
        } yield {
          (maybeUserAnswers, subscriptionEither) match {
            case (_, Left(_)) => Future.successful(InternalServerError("Subscription not found for Change Group Lead"))
            case (None, _) => Future.successful(InternalServerError("User Answers not found for Change Group Lead"))
            case (Some(userAnswers), Right(subscription)) =>
              changeGroupRep(pptReference, userAnswers, subscription, request)
              .map(_ => Ok("Updated Group Lead as per userAnswers"))
          }
        }
      }.flatten
    }

  private def changeGroupRep(pptReference: String, userAnswers: UserAnswers, subscription: SubscriptionDisplayResponse, 
    request: AuthorizedRequest[_]) (implicit hc: HeaderCarrier) = {

    val subscriptionUpdateRequest = changeGroupLeadService.createSubscriptionUpdateRequest(subscription, userAnswers)
    val nrsSubscriptionUpdateSubmission = changeGroupLeadService.createNrsSubscriptionUpdateSubmission(subscriptionUpdateRequest, userAnswers)
    subscriptionsConnector
      .updateSubscription(pptReference, subscriptionUpdateRequest)
      .map {
        subscriptionUpdateResponse => nonRepudiationService.submitNonRepudiation(
            nrsSubscriptionUpdateSubmission.toJsonString, 
            subscriptionUpdateResponse.processingDate, 
            pptReference,
            request.headers.headers.toMap
          )
      }
  }
}
