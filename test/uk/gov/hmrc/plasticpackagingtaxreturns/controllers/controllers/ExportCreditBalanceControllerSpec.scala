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
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ExportCreditBalanceController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ReturnObligationFromDateGettable, ReturnObligationToDateGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditsCalculationResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditClaim, CreditsCalculationService, TaxRateService}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class ExportCreditBalanceControllerSpec extends PlaySpec with BeforeAndAfterEach {

  private val mockSessionRepo: SessionRepository = mock[SessionRepository]
  private val mockCreditsCalcService = mock[CreditsCalculationService]
  private val mockAvailableCreditsService = mock[AvailableCreditService]
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val taxRateService = mock[TaxRateService]
  
  private val userAnswers = UserAnswers("user-answers-id")
    .setUnsafe(ReturnObligationFromDateGettable, LocalDate.now)
    .setUnsafe(ReturnObligationToDateGettable, LocalDate.of(2023, 5, 1))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAvailableCreditsService, mockSessionRepo, mockCreditsCalcService, taxRateService)
    when(mockSessionRepo.get(any)) thenReturn Future.successful(Some(userAnswers))
    when(mockAvailableCreditsService.getBalance(any)(any)).thenReturn(Future.successful(BigDecimal(200)))
  }

  val sut = new ExportCreditBalanceController(
    new FakeAuthenticator(cc),
    mockSessionRepo,
    mockCreditsCalcService,
    mockAvailableCreditsService,
    taxRateService,
    cc,
  )(global)

  "get" must {
    def now: LocalDate = LocalDate.now
    "return 200 response with correct values" in {
      when(mockCreditsCalcService.totalRequestedCredit(any)).thenReturn(CreditClaim(100L, BigDecimal(20), 0.2))
      when(taxRateService.lookupTaxRateForPeriod(any)).thenReturn(0.20)

      val result = sut.get("url-ppt-ref")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(
        CreditsCalculationResponse(
          BigDecimal(200),
          BigDecimal(20),
          100L,
          0.20
        )
      )

      withClue("session repo called with the cache key"){
        verify(mockSessionRepo).get(s"some-internal-ID-some-ppt-ref")
      }

      withClue("credits calculation service is called"){
        verify(mockCreditsCalcService).totalRequestedCredit(userAnswers)
      }

      withClue("fetch available credit balance"){
        verify(mockAvailableCreditsService).getBalance(refEq(userAnswers))(any)
      }
    }

    "return 500 internal error" when {
      "session repo fails" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.failed(new Exception("boom")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("boom"))

        withClue("available credit should not have been called"){
          verify(mockAvailableCreditsService, never()).getBalance(any)(any)
        }
        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any)
        }
      }

      "session repo is empty" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.successful(None))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("UserAnswers is empty"))

        withClue("available credit should not have been called"){
          verify(mockAvailableCreditsService, never()).getBalance(any)(any)
        }
        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any)
        }
      }

      "The available credits service fails" in {
        when(mockSessionRepo.get(any)).thenReturn(Future.successful(Some(UserAnswers(""))))
        when(mockAvailableCreditsService.getBalance(any)(any)).thenReturn(Future.failed(new Exception("test message")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("test message"))

        withClue("calculator should not have been called"){
          verify(mockCreditsCalcService, never()).totalRequestedCredit(any)
        }
      }

      // TODO are these effectively the same test, above and below?
      
      "getBalance returns an error" in {
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(ReturnObligationFromDateGettable, now)

        when(mockSessionRepo.get(any)).thenReturn(Future.successful(Some(userAnswers)))
        when(mockAvailableCreditsService.getBalance(any)(any)).thenReturn(Future.failed(new Exception("test error")))

        val result = sut.get("url-ppt-ref")(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("test error"))
      }

      "credits calculation fails for some reason" in {
        when(mockCreditsCalcService.totalRequestedCredit(any)) thenThrow new RuntimeException("bang")
        val result = sut.get("url-ppt-ref")(FakeRequest())
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("message" -> JsString("bang"))
      }
    }
    
  }
}
