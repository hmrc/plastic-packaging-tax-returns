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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.{reset, when}
import org.mockito.MockitoSugar.verify
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.UNPROCESSABLE_ENTITY
import play.api.libs.json.Json.toJson
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers.{OK, await, contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.CalculationsController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.CreditClaim
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{AmendsCalculations, Calculations}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.AmendReturnValues
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, PPTCalculationService}
import uk.gov.hmrc.plasticpackagingtaxreturns.support.{AmendTestHelper, ReturnTestHelper}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.TaxRateTable

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalculationsControllerSpec
  extends PlaySpec
    with BeforeAndAfterEach
    with AuthTestSupport {


  private val userAnswers: UserAnswers        = UserAnswers("id").copy(data = ReturnTestHelper.returnWithCreditsDataJson)
  private val invalidUserAnswers: UserAnswers = UserAnswers("id").copy(data = ReturnTestHelper.invalidReturnsDataJson)

  private val sessionRepository: SessionRepository = mock[SessionRepository]
  private val availableCreditService: AvailableCreditService = mock[AvailableCreditService]
  private val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val pptCalculationService = mock[PPTCalculationService]
  private val creditsCalculationService = mock[CreditsCalculationService]
  private val taxRateTable = mock[TaxRateTable]

  private val sut = new CalculationsController(
    new FakeAuthenticator(cc),
    sessionRepository,
    cc,
    pptCalculationService,
    creditsCalculationService,
    availableCreditService,
    taxRateTable
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(sessionRepository, availableCreditService, pptCalculationService, creditsCalculationService, taxRateTable)

    when(sessionRepository.get(any[String])) thenReturn Future.successful(Some(userAnswers))
    when(availableCreditService.getBalance(any)(any)) thenReturn Future.successful(BigDecimal(0))
    when(taxRateTable.lookupRateFor(any)).thenReturn(0.123)
  }

  "calculateSubmit" should {

    "return OK response and the calculation" in {
      val expected: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15,
          packagingTotal = 100, isSubmittable = true, taxRate = 0.123)
      when(creditsCalculationService.totalRequestedCredit(any)).thenReturn(CreditClaim(100L, 200, 2.0))
      when(pptCalculationService.calculate(any)).thenReturn(expected)

      val result: Future[Result] = sut.calculateSubmit(pptReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(expected)
    }

    "request credits" in {
      val expected: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15,
        packagingTotal = 100, isSubmittable = true, taxRate = 0.123)
      when(creditsCalculationService.totalRequestedCredit(any)).thenReturn(CreditClaim(100L, 200, 2.0))
      when(pptCalculationService.calculate(any)).thenReturn(expected)

      await(sut.calculateSubmit(pptReference)(FakeRequest()))

      verify(availableCreditService).getBalance(ArgumentMatchers.eq(userAnswers))(any)
      verify(creditsCalculationService).totalRequestedCredit(ArgumentMatchers.eq(userAnswers))
    }

    "return un-processable entity" when {
      "has an incomplete cache" in {
        when(sessionRepository.get(any[String])) thenReturn Future.successful(Some(invalidUserAnswers))

        val result: Future[Result] = sut.calculateSubmit(pptReference)(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }

      "there is no cache" in {
        when(sessionRepository.get(any[String])) thenReturn Future.successful(None)

        val result: Future[Result] = sut.calculateSubmit(pptReference)(FakeRequest())

        status(result) mustBe UNPROCESSABLE_ENTITY
      }
    }
  }

  "calculateAmends" should {
    "return OK response and the calculation" in {

      val originalReturnAsCalc = Calculations(44,220,0,250, true, taxRate = 0.123)

      val amendCalc = Calculations(2, 3, 4, 5, false, taxRate = 0.123)

      when(sessionRepository.get(any[String]))
        .thenReturn(Future.successful(Some(UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends))))
      when(pptCalculationService.calculate(any)).thenReturn(amendCalc)

      val result = sut.calculateAmends(pptReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(AmendsCalculations(originalReturnAsCalc, amendCalc))
    }

    "calculate amend return" in {
      val ans = UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends)

      when(sessionRepository.get(any[String]))
        .thenReturn(Future.successful(Some(ans)))
      when(pptCalculationService.calculate(any)).thenReturn(Calculations(1,0,0,10, true, taxRate = 0.123))

      await(sut.calculateAmends(pptReference)(FakeRequest()))

      val expected = AmendReturnValues("21C4", LocalDate.of(2022, 6, 30),100, 1, 2, 5, 3,5, "submission12")
      verify(pptCalculationService).calculate(ArgumentMatchers.eq(expected))
    }
  }
}
