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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.libs.json.Json.{arr, obj}
import play.api.mvc.Result
import play.api.mvc.Results.{Ok, UnprocessableEntity}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.AvailableCreditDateRangesController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.Gettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationToDateGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersService.notFoundMsg
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditDateRangesService, UserAnswersService}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AvailableCreditDateRangesControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext  = ExecutionContext.Implicits.global
  private val controllerComponents   = Helpers.stubControllerComponents()
  private val service                = mock[AvailableCreditDateRangesService]
  private val authenticator          = spy(new FakeAuthenticator(controllerComponents))
  private val subscriptionsConnector = mock[SubscriptionsConnector]
  private val subscription           = mock[SubscriptionDisplayResponse]
  private val userAnswers            = mock[UserAnswers]

  private val sessionRepository  = mock[SessionRepository]
  private val userAnswersService = new UserAnswersService(sessionRepository)

  val sut = new AvailableCreditDateRangesController(
    service,
    authenticator,
    userAnswersService,
    controllerComponents,
    subscriptionsConnector
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(service, sessionRepository, authenticator, subscriptionsConnector, subscription, userAnswers)

    when(subscription.taxStartDate()) thenReturn LocalDate.of(2001, 2, 3)
    when(subscriptionsConnector.getSubscriptionFuture(any)(any)) thenReturn Future.successful(subscription)
    when(userAnswers.getOrFail(any[Gettable[LocalDate]])(any, any)) thenReturn LocalDate.of(2004, 5, 6)

    when(service.calculate(any, any)) thenReturn Seq(
      CreditRangeOption(LocalDate.of(2007, 8, 9), LocalDate.of(2008, 11, 12)),
      CreditRangeOption(LocalDate.of(2009, 8, 9), LocalDate.of(2010, 11, 12))
    )
  }

  "get" must {

    "use authenticator" in {
      Try(await(sut.get("pptRef")(FakeRequest())))
      verify(authenticator).authorisedAction(any, eqTo("pptRef"))(any)
    }

    "fetch user answers and subscription" in {
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(userAnswers)))
      Try(await(sut.get("ppt-ref")(FakeRequest())))
      verify(subscriptionsConnector).getSubscriptionFuture(eqTo("ppt-ref"))(any)
    }

    "calculate available dates using return's obligation and the subscription's tax start date" in {
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(userAnswers)))
      val result = await(sut.get("ppt-ref")(FakeRequest()))
      verify(subscription).taxStartDate()
      verify(userAnswers).getOrFail(ReturnObligationToDateGettable)
      verify(service).calculate(returnEndDate = LocalDate.of(2004, 5, 6), taxStartDate = LocalDate.of(2001, 2, 3))

      contentAsJson(Future.successful(result)) mustBe arr(
        obj("from" -> "2007-08-09", "to" -> "2008-11-12"),
        obj("from" -> "2009-08-09", "to" -> "2010-11-12")
      )
    }

    "return 200 and list of dates" in {
      when(sessionRepository.get(FakeAuthenticator.cacheKey))
        .thenReturn(
          Future.successful(
            Some(
              UserAnswers("id")
                .setOrFail(ReturnObligationToDateGettable.path, LocalDate.of(1996, 3, 27))
            )
          )
        )
      when(service.calculate(any, any)).thenReturn(Seq(CreditRangeOption(
        LocalDate.of(1996, 3, 27),
        LocalDate.of(1998, 5, 14)
      )))
      val dates = """[{"from": "1996-03-27", "to": "1998-05-14"}]"""

      val result = sut.get("pptRef")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(dates)
      verify(service).calculate(any, any)
    }

    "error" when {

      "user answers is missing" in {
        when(sessionRepository.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(None))

        val result = await(sut.get("pptRef")(FakeRequest()))
        result mustBe UnprocessableEntity(notFoundMsg)
      }

      "user answers is missing the return toDate" in {
        when(sessionRepository.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(UserAnswers("123"))))

        val ex = intercept[IllegalStateException](await(sut.get("pptRef")(FakeRequest())))
        ex.getMessage mustBe s"${ReturnObligationToDateGettable.path} is missing from user answers"
      }

      "subscription api call failed with exception" in {
        when(sessionRepository.get(FakeAuthenticator.cacheKey)).thenReturn(Future.successful(Some(userAnswers)))
        when(subscriptionsConnector.getSubscriptionFuture(any)(any)) thenReturn Future.failed(
          new IllegalStateException("boom")
        )
        the[IllegalStateException] thrownBy await(sut.get("pptRef")(FakeRequest())) must
          have message "boom"
      }

    }
  }

}
