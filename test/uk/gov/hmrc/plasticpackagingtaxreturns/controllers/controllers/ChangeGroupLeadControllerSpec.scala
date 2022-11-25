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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{IM_A_TEAPOT, INTERNAL_SERVER_ERROR, OK}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ChangeGroupLeadController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.ChangeGroupLeadService

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ChangeGroupLeadControllerSpec extends PlaySpec with BeforeAndAfterEach {

  val userAnswers = UserAnswers("user-answers-id")
  val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]
  val subscriptionUpdateRequest = mock[SubscriptionUpdateRequest]
  val mockSessionRepo = mock[SessionRepository]
  val mockChangeGroupLeadService = mock[ChangeGroupLeadService]
  val mockSubscriptionsConnector = mock[SubscriptionsConnector]
  val cc: ControllerComponents = Helpers.stubControllerComponents()

  val pptRef = "some-ppt-ref"

  val sut = new ChangeGroupLeadController(
    new FakeAuthenticator(cc),
    mockSessionRepo,
    cc,
    changeGroupLeadService = mockChangeGroupLeadService,
    subscriptionsConnector = mockSubscriptionsConnector
  )(global)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockSessionRepo,
      subscriptionDisplayResponse,
      subscriptionUpdateRequest,
      mockChangeGroupLeadService,
      mockSubscriptionsConnector)

    when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
    when(mockSessionRepo.clear(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(true))
    when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any())).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))
    when(mockChangeGroupLeadService.changeSubscription(subscriptionDisplayResponse, userAnswers)).thenReturn(subscriptionUpdateRequest)
    when(mockSubscriptionsConnector.updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any())).thenReturn(Future.successful(mock[SubscriptionUpdateResponse]))
  }

  "change" must {
    "update the group lead" in {
      val result = sut.change(pptRef)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) mustBe "Updated Group Lead as per userAnswers"
      verify(mockSubscriptionsConnector).updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any())
    }

    "must clear userAnswer on success" in {
      val result = sut.change(pptRef)(FakeRequest())

      status(result) mustBe OK
      verify(mockSessionRepo).clear(FakeAuthenticator.cacheKey)
    }

    "error" when {
      object TestException extends Exception("boom!")
      "get user answers are not there" in {
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(None))

        val result = sut.change(pptRef)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "User Answers not found for Change Group Lead"
      }
      "get subscription does not return a subscription" in {
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any())).thenReturn(Future.successful(Left(HttpResponse(IM_A_TEAPOT, "test"))))

        val result = sut.change(pptRef)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "Subscription not found for Change Group Lead"
      }
      "get user answers fails" in {
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.failed(TestException))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
      "get subscription fails" in {
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any())).thenReturn(Future.failed(TestException))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
      "change service fails" in {
        when(mockChangeGroupLeadService.changeSubscription(subscriptionDisplayResponse, userAnswers)).thenThrow(new IllegalStateException("checked exception"))

        intercept[IllegalStateException](await(sut.change(pptRef)(FakeRequest())))
      }
      "update subscription fails" in {
        when(mockSubscriptionsConnector.updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any())).thenReturn(Future.failed(TestException))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
    }
  }

}
