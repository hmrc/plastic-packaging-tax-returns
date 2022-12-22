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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{AmendsCalculations, Calculations}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, OriginalReturnForAmendValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.Credit
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, PPTCalculationService}
import uk.gov.hmrc.plasticpackagingtaxreturns.support.{AmendTestHelper, ReturnTestHelper}

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

  private val sut = new CalculationsController(
    new FakeAuthenticator(cc),
    sessionRepository,
    cc,
    pptCalculationService,
    creditsCalculationService,
    availableCreditService
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(sessionRepository, availableCreditService, pptCalculationService, creditsCalculationService)

    when(sessionRepository.get(any[String])) thenReturn Future.successful(Some(userAnswers))
    when(availableCreditService.getBalance(any)(any)) thenReturn Future.successful(BigDecimal(0))
  }

  "calculateSubmit" should {

    "return OK response and the calculation" in {
      val expected: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15,
          packagingTotal = 100, totalRequestCreditInPounds = 0, isSubmittable = true)
      when(creditsCalculationService.totalRequestedCredit(any)).thenReturn(Credit(100L, 200))
      when(pptCalculationService.calculateNewReturn(any,any)).thenReturn(expected)

      val result: Future[Result] = sut.calculateSubmit(pptReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(expected)
    }

    "request credits" in {
      val expected: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15,
        packagingTotal = 100, totalRequestCreditInPounds = 0, isSubmittable = true)
      when(creditsCalculationService.totalRequestedCredit(any)).thenReturn(Credit(100L, 200))
      when(pptCalculationService.calculateNewReturn(any,any)).thenReturn(expected)

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

      val originalCal = Calculations(1,0,0,10,20, true)

      when(sessionRepository.get(any[String]))
        .thenReturn(Future.successful(Some(UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends))))
      when(pptCalculationService.calculateAmendReturn(any,any)).thenReturn(originalCal)

      val result = sut.calculateAmends(pptReference)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(AmendsCalculations(originalCal, originalCal))
    }

    "calculate original return" in {
      val originalCal = Calculations(1,0,0,10,20, true)
      val ans = UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends)

      when(sessionRepository.get(any[String]))
        .thenReturn(Future.successful(Some(ans)))
      when(pptCalculationService.calculateAmendReturn(any,any)).thenReturn(originalCal)

      await(sut.calculateAmends(pptReference)(FakeRequest()))

      val expected = OriginalReturnForAmendValues(periodKey = "N/A", 250, 0, 0, 10,5, "submission12")
      verify(pptCalculationService).calculateAmendReturn(ArgumentMatchers.eq(ans), ArgumentMatchers.eq(expected))
    }

    "calculate amend return" in {
      val ans = UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends)

      when(sessionRepository.get(any[String]))
        .thenReturn(Future.successful(Some(ans)))
      when(pptCalculationService.calculateAmendReturn(any,any)).thenReturn(Calculations(1,0,0,10,20, true))

      await(sut.calculateAmends(pptReference)(FakeRequest()))

      val expected = AmendReturnValues("21C4", 100, 1, 2, 3,5, "submission12")
      verify(pptCalculationService).calculateAmendReturn(ArgumentMatchers.eq(ans), ArgumentMatchers.eq(expected))
    }
  }
}
