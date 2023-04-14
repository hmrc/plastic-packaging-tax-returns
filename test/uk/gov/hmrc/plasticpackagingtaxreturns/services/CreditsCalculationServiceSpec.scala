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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate

class CreditsCalculationServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockConversion: WeightToPoundsConversionService = mock[WeightToPoundsConversionService]
  val captor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])

  val sut = new CreditsCalculationService(mockConversion)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversion)
    when(mockConversion.weightToCredit(any(), any())) thenReturn CreditClaim(0, 0, 3.14)
  }

  private def converterJustAddsOne() =
    when(mockConversion.weightToCredit(any(), any())).thenAnswer { 
      a => 
        val addOne = a.getArgument(1).asInstanceOf[Long] + 1
        CreditClaim(0, BigDecimal(addOne), 1.0)
    }

  // TODO split out tests covering CreditsAnswer

  val theWrongDate: LocalDate = LocalDate.of(2022, 4, 1) // TODO date percolator

  "totalRequestCredit" must {
    
    "return 0" when {
      "both fields are empty" in { 
        // TODO should complain about missing user-answers?
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(0, 1, 1.0)
      }
    }

    "total the weight" when {
      
      "both answers are missing" in {
        val userAnswers = UserAnswers("user-answers-id")
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(weight = 0, moneyInPounds = 0, taxRate = 3.14)
        verify(mockConversion).weightToCredit(theWrongDate, 0L)
      }
      
      "both answers answered with false" in {
        val userAnswers = UserAnswers("user-answers-id").setAll(
          "exportedCredits" -> CreditsAnswer(false, None), 
          "convertedCredits" -> CreditsAnswer(false, None)
        )
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(weight = 0, moneyInPounds = 0, taxRate = 3.14)
        verify(mockConversion).weightToCredit(theWrongDate, 0L)
      }
      
      "both answers answered are only partially answered" in {
        val userAnswers = UserAnswers("user-answers-id") .setAll(
          "exportedCredits" -> CreditsAnswer(true, None), 
          "convertedCredits" -> CreditsAnswer(true, None)
        )
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(weight = 0, moneyInPounds = 0, taxRate = 3.14)
        verify(mockConversion).weightToCredit(theWrongDate, 0L)
      }

      "converted is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setAll("convertedCredits" -> CreditsAnswer(true, Some(5L)))
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(0, 6, 1.0)
      }

      "exported is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setAll("exportedCredits" -> CreditsAnswer(true, Some(7L)))
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(0, 8, 1.0)
      }

      "both are supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id").setAll(
          "convertedCredits" -> CreditsAnswer(true, Some(5L)),
          "exportedCredits" -> CreditsAnswer(true, Some(7L))
        )
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(0, 13, 1.0)
      }
    }

    "convert the weight in to pounds(£) and return it unchanged" in {
      when(mockConversion.weightToCredit(any(), any())).thenReturn(CreditClaim(0, 42.69, 1.1))
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))
        .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

      sut.totalRequestedCredit(userAnswers) mustBe CreditClaim(0, 42.69, 1.1)

      verify(mockConversion).weightToCredit(any(), eqTo(12L)) // TODO date percolator
      verify(mockConversion, never()).weightToDebit(any(), any())
    }
  }

}
