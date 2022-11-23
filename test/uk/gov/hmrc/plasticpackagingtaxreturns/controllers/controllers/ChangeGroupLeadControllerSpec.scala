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

import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar.{never, reset, verify, when}
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
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ChangeGroupLeadControllerSpec extends PlaySpec with BeforeAndAfterEach {

  private val mockSessionRepo = mock[SessionRepository]
  private val mockChangeGroupLeadService = mock[ChangeGroupLeadService]
  private val mockSubscriptionsConnector = mock[SubscriptionsConnector]
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val nonRepudiationService = mock[NonRepudiationService]
  private val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]
  private val userAnswers = UserAnswers("user-answers-id")

  val pptRef = "some-ppt-ref"

  val sut = new ChangeGroupLeadController(
    new FakeAuthenticator(cc),
    mockSessionRepo,
    cc,
    mockChangeGroupLeadService,
    mockSubscriptionsConnector,
    nonRepudiationService
  )(global)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSessionRepo, mockChangeGroupLeadService, mockSubscriptionsConnector, nonRepudiationService)

    when(mockSubscriptionsConnector.getSubscription(any)(any)) thenReturn Future.successful(Right(subscriptionDisplayResponse))
    when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
    when(mockSubscriptionsConnector.updateSubscription(any, any)(any)) thenReturn Future.successful(mock[SubscriptionUpdateResponse])
  }

  "change" must {
    "update the group lead" in {
      val subscriptionUpdateRequest = mock[SubscriptionUpdateRequest]
      when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
//      when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))
      when(mockChangeGroupLeadService.changeSubscription(subscriptionDisplayResponse, userAnswers)).thenReturn(subscriptionUpdateRequest)
      when(mockSubscriptionsConnector.updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any)).thenReturn(Future.successful(mock[SubscriptionUpdateResponse]))

      val result = sut.change(pptRef)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) mustBe "Updated Group Lead as per userAnswers"
      verify(mockSubscriptionsConnector).updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any)
    }

    "calls the NRS service when update subscription is successful" in {
      await(sut.change("ref").apply(FakeRequest()))
      verify(nonRepudiationService).submitNonRepudiation(any, any, any, any) (any)
    }

    "not call the NRS service when update subscription fails" in {
      when(mockSubscriptionsConnector.updateSubscription(any, any)(any)) thenReturn Future.failed(new Exception)
      an [Exception] must be thrownBy await(sut.change("ref").apply(FakeRequest()))
      verify(nonRepudiationService, never).submitNonRepudiation(any, any, any, any) (any)
    }

    "error" when {
      
      object TestException extends Exception("boom!")
      
      "get user answers are not there" in {
        val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(None))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))

        val result = sut.change(pptRef)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "User Answers not found for Change Group Lead"
      }
      
      "get subscription does not return a subscription" in {
        val userAnswers = UserAnswers("user-answers-id")
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Left(HttpResponse(IM_A_TEAPOT, "test"))))

        val result = sut.change(pptRef)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "Subscription not found for Change Group Lead"
      }
      
      "get user answers fails" in {
        val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.failed(TestException))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
      
      "get subscription fails" in {
        val userAnswers = UserAnswers("user-answers-id")
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.failed(TestException))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
      
      "change service fails" in {
        val userAnswers = UserAnswers("user-answers-id")
        val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]

        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))
        when(mockChangeGroupLeadService.changeSubscription(subscriptionDisplayResponse, userAnswers)).thenThrow(new IllegalStateException("checked exception"))

        intercept[IllegalStateException](await(sut.change(pptRef)(FakeRequest())))
      }
      
      "update subscription fails" in {
        val userAnswers = UserAnswers("user-answers-id")
        val subscriptionDisplayResponse = mock[SubscriptionDisplayResponse]
        val subscriptionUpdateRequest = mock[SubscriptionUpdateRequest]
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Right(subscriptionDisplayResponse)))
        when(mockChangeGroupLeadService.changeSubscription(subscriptionDisplayResponse, userAnswers)).thenReturn(subscriptionUpdateRequest)
        when(mockSubscriptionsConnector.updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any)).thenReturn(Future.failed(TestException))

        intercept[TestException.type](await(sut.change(pptRef)(FakeRequest())))
      }
    }
  }

}
