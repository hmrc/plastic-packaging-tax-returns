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

import org.mockito.ArgumentMatchers.refEq
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.MockitoSugar.reset
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ExportCreditBalanceController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ReturnObligationFromDateGettable, ReturnObligationToDateGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{CreditCalculation, TaxablePlastic, UserAnswers}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, UserAnswersService}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ExportCreditBalanceControllerSpec extends PlaySpec with BeforeAndAfterEach {

  implicit val ec: ExecutionContext              = ExecutionContext.Implicits.global
  private val mockSessionRepo: SessionRepository = mock[SessionRepository]
  private val creditsCalculationService          = mock[CreditsCalculationService]
  private val mockAvailableCreditsService        = mock[AvailableCreditService]
  private val cc: ControllerComponents           = Helpers.stubControllerComponents()
  private val userAnswersService                 = new UserAnswersService(mockSessionRepo)

  private val userAnswers = UserAnswers("user-answers-id")
    .setUnsafe(ReturnObligationFromDateGettable, LocalDate.now)
    .setUnsafe(ReturnObligationToDateGettable, LocalDate.of(2023, 5, 1))

  private val exampleCreditCalculation = CreditCalculation(
    availableCreditInPounds = 200,
    totalRequestedCreditInPounds = 20,
    totalRequestedCreditInKilograms = 100,
    canBeClaimed = true,
    credit = Map("a-key" -> TaxablePlastic(100L, BigDecimal(20), 0.2))
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAvailableCreditsService, mockSessionRepo, creditsCalculationService)
    when(mockSessionRepo.get(any)) thenReturn Future.successful(Some(userAnswers))
    when(mockAvailableCreditsService.getBalance(any)(any)).thenReturn(Future.successful(BigDecimal(200)))
  }

  val sut =
    new ExportCreditBalanceController(new FakeAuthenticator(cc), creditsCalculationService, mockAvailableCreditsService, cc, userAnswersService)(ec)

  "get" must {

    "return 200 response with correct values" in {
      when(creditsCalculationService.totalRequestedCredit(any, any)) thenReturn exampleCreditCalculation

      val result = sut.get("url-ppt-ref")(FakeRequest())

      status(result) mustBe OK
      verify(creditsCalculationService).totalRequestedCredit(any, any)
      contentAsJson(result) mustBe Json.toJson(exampleCreditCalculation)

      withClue("session repo called with the cache key") {
        verify(mockSessionRepo).get(s"some-internal-ID-some-ppt-ref")
      }
      withClue("fetch available credit balance") {
        verify(mockAvailableCreditsService).getBalance(refEq(userAnswers))(any)
      }
      withClue("credits calculation service is called") {
        verify(creditsCalculationService).totalRequestedCredit(userAnswers, availableCreditInPounds = 200)
      }
    }

    "return 500 internal error" when {
      "session repo fails" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.failed(new Exception("boom")))

        the[Exception] thrownBy {
          await(sut.get("url-ppt-ref")(FakeRequest()))
        } must have message "boom"

        withClue("available credit should not have been called") {
          verify(mockAvailableCreditsService, never()).getBalance(any)(any)
        }
        withClue("calculator should not have been called") {
          verify(creditsCalculationService, never()).totalRequestedCredit(any, any)
        }
      }

      "session repo is empty" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.successful(None))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY

        withClue("available credit should not have been called") {
          verify(mockAvailableCreditsService, never()).getBalance(any)(any)
        }
        withClue("calculator should not have been called") {
          verify(creditsCalculationService, never()).totalRequestedCredit_old(any)
        }
      }

      "The available credits service fails" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.successful(Some(UserAnswers(""))))
        when(mockAvailableCreditsService.getBalance(any)(any)).thenReturn(Future.failed(new Exception("test message")))

        the[Exception] thrownBy {
          await(sut.get("url-ppt-ref")(FakeRequest()))
        } must have message "test message"

        withClue("calculator should not have been called") {
          verify(creditsCalculationService, never()).totalRequestedCredit_old(any)
        }
      }

      "complain about missing period end-date / credits calculation fails for some other reason" in {
        when(creditsCalculationService.totalRequestedCredit(any, any)) thenThrow new RuntimeException("bang")
        the[Exception] thrownBy {
          await(sut.get("url-ppt-ref")(FakeRequest()))
        } must have message "bang"
      }
    }

  }
}
