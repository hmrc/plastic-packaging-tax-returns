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
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsPath, Json}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.services.CreditsCalculationService.CreditClaimed
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

class CreditsCalculationServiceSpec extends PlaySpec with BeforeAndAfterEach {

  val mockConversion: WeightToPoundsConversionService = mock[WeightToPoundsConversionService]
  val captor: ArgumentCaptor[Long] = ArgumentCaptor.forClass(classOf[Long])

  val sut = new CreditsCalculationService(mockConversion)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConversion)
    when(mockConversion.weightToCredit(any())) thenReturn BigDecimal(0)
  }

  private def converterJustAddsOne() =
    when(mockConversion.weightToCredit(any())).thenAnswer(a => BigDecimal(a.getArgument(0).asInstanceOf[Long] + 1))

  // TODO split out tests covering CreditsAnswer
  
  "totalRequestCredit" must {
    
    "return 0" when {
      "both fields are empty" in { 
        // TODO should complain about missing user-answers?
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(0, 1)
      }
    }

    "total the weight" when {
      
      "both answers are missing" in {
        val userAnswers = UserAnswers("user-answers-id")
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(weight = 0, moneyInPounds = 0)
        verify(mockConversion).weightToCredit(0L)
      }
      
      "both answers answered with false" in {
        val userAnswers = UserAnswers("user-answers-id")
          .setAll("exportedCredits" -> CreditsAnswer(false, None))
          .setAll("convertedCredits" -> CreditsAnswer(false, None))
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(weight = 0, moneyInPounds = 0)
        verify(mockConversion).weightToCredit(0L)
      }
      
      "both answers answered are only partially answered" in {
        val userAnswers = UserAnswers("user-answers-id")
          .setAll("exportedCredits" -> CreditsAnswer(true, None))
          .setAll("convertedCredits" -> CreditsAnswer(true, None))
        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(weight = 0, moneyInPounds = 0)
        verify(mockConversion).weightToCredit(0L)
      }

      "converted is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))

        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(5, 6)
      }
      
      "exported is supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(7, 8)
      }
      
      "both are supplied" in {
        converterJustAddsOne()
        val userAnswers = UserAnswers("user-answers-id")
          .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))
          .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

        sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(12, 13)
      }
    }

    "convert the weight in to pounds(£) and return it unchanged" in {
      val expectedPounds = 42.69
      when(mockConversion.weightToCredit(any())).thenReturn(expectedPounds)
      val userAnswers = UserAnswers("user-answers-id")
        .setUnsafe(JsPath \ "convertedCredits", CreditsAnswer(true, Some(5L)))
        .setUnsafe(JsPath \ "exportedCredits", CreditsAnswer(true, Some(7L)))

      sut.totalRequestedCredit(userAnswers) mustBe CreditClaimed(12L, expectedPounds)

      verify(mockConversion).weightToCredit(12L)
      verify(mockConversion, never()).weightToDebit(any(), any())
    }
  }

}
