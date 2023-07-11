/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.MockitoSugar.{mock, never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{IM_A_TEAPOT, INTERNAL_SERVER_ERROR, OK}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.nonRepudiation.NrsSubscriptionUpdateSubmission
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ChangeGroupLeadController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.ChangeGroupLeadService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService.NotableEvent
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpResponse

import java.time.ZonedDateTime
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
  private val subscriptionUpdateResponse = mock[SubscriptionUpdateSuccessfulResponse]
  private val nrsSubscriptionUpdateSubmission = mock[NrsSubscriptionUpdateSubmission]
  private val subscriptionUpdateRequest = mock[SubscriptionUpdateRequest]

  object TestException extends Exception("boom!")

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
    reset(mockSessionRepo, mockChangeGroupLeadService, mockSubscriptionsConnector, nonRepudiationService,
      subscriptionUpdateResponse, subscriptionUpdateRequest)

    when(mockSubscriptionsConnector.getSubscription(any)(any)) thenReturn Future.successful(Right(subscriptionDisplayResponse))
    when(mockSubscriptionsConnector.updateSubscription(any, any)(any)) thenReturn Future.successful(subscriptionUpdateResponse)
    
    when(mockChangeGroupLeadService.createSubscriptionUpdateRequest(subscriptionDisplayResponse, userAnswers)).thenReturn(subscriptionUpdateRequest)
    when(mockChangeGroupLeadService.createNrsSubscriptionUpdateSubmission(any, any)) thenReturn nrsSubscriptionUpdateSubmission
    
    when(mockSessionRepo.get(any)) thenReturn Future.successful(Some(userAnswers))
    when(mockSessionRepo.clear(any)) thenReturn Future.successful(true)
  }

  "change" must {

    "update the group lead" in {
      val result = sut.change("some-ppt-ref")(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) mustBe "Updated Group Lead as per userAnswers"
      verify(mockSubscriptionsConnector).updateSubscription(same("some-ppt-ref"), same(subscriptionUpdateRequest)) (any)
      verify(mockSubscriptionsConnector).getSubscription(same("some-ppt-ref")) (any)
      verify(mockSessionRepo).get("some-internal-ID-some-ppt-ref")
    }

    "pass event name, date and ppt ref to NRS" in {
      val processingDate = mock[ZonedDateTime]
      when(subscriptionUpdateResponse.processingDate) thenReturn processingDate
      await(sut.change("ref").apply(FakeRequest()))
      verify(nonRepudiationService).submitNonRepudiation(same(NotableEvent.PptSubscription), any, same(processingDate), eqTo("some-ppt-ref"), any) (any)
    }

    "pass user header to NRS" in {
      val request = FakeRequest().withHeaders(("harder", "than it should be"))
      await(sut.change("ref").apply(request))
      val headers = Map("Host" -> "localhost", "harder" -> "than it should be")
      verify(nonRepudiationService).submitNonRepudiation(any, any, any, any, eqTo(headers)) (any)
    }
    
    "pass payload to NRS" in {
      when(nrsSubscriptionUpdateSubmission.toJsonString) thenReturn "nrs-payload"
      await(sut.change("ref").apply(FakeRequest()))
      verify(nonRepudiationService).submitNonRepudiation(any, eqTo("nrs-payload"), any, any, any) (any)
    }

    "not call the NRS when update subscription fails" in {
      when(mockSubscriptionsConnector.updateSubscription(any, any)(any)) thenReturn Future.failed(new Exception)
      an [Exception] must be thrownBy await(sut.change("ref").apply(FakeRequest()))
      verify(nonRepudiationService, never).submitNonRepudiation(any, any, any, any, any) (any)
    }

    "must clear userAnswers when subscription update successful" in {
      await(sut.change("some-ppt-ref")(FakeRequest()))
      verify(mockSessionRepo).clearUserAnswers("some-ppt-ref", FakeAuthenticator.cacheKey)
    }

    "must not clear userAnswers shen subscription update fails" in {
      when(mockSubscriptionsConnector.updateSubscription(any, any)(any)) thenReturn Future.failed(TestException)
      an[Exception] mustBe thrownBy {
        await(sut.change("some-ppt-ref")(FakeRequest()))
      }
      verify(mockSessionRepo, never).clear(any)
    }

    "error" when {
     
      "get user answers are not there" in {
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(None))

        val result = sut.change("some-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "User Answers not found for Change Group Lead"
      }
      
      "get subscription does not return a subscription" in {
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any)).thenReturn(Future.successful(Left(EisHttpResponse(IM_A_TEAPOT, "test", "123"))))

        val result = sut.change("some-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe "Subscription not found for Change Group Lead"
      }
      
      "get user answers fails" in {
        when(mockSessionRepo.get(FakeAuthenticator.cacheKey)).thenReturn(Future.failed(TestException))
        intercept[TestException.type](await(sut.change("some-ppt-ref")(FakeRequest())))
      }
      
      "get subscription fails" in {
        when(mockSubscriptionsConnector.getSubscription(refEq(FakeAuthenticator.pptRef))(any))
          .thenReturn(Future.failed(TestException))
        intercept[TestException.type](await(sut.change("some-ppt-ref")(FakeRequest())))
      }
      
      "change service fails" in {
        when(mockChangeGroupLeadService.createSubscriptionUpdateRequest(subscriptionDisplayResponse, userAnswers))
          .thenThrow(new IllegalStateException("checked exception"))
        intercept[IllegalStateException](await(sut.change("some-ppt-ref")(FakeRequest())))
      }
      
      "update subscription fails" in {
        when(mockSubscriptionsConnector.updateSubscription(refEq(FakeAuthenticator.pptRef), refEq(subscriptionUpdateRequest))(any))
          .thenReturn(Future.failed(TestException))
        intercept[TestException.type](await(sut.change("some-ppt-ref")(FakeRequest())))
      }
    }
  }

}
