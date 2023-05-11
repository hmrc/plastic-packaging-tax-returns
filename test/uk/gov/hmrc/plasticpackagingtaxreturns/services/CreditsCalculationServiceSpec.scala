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

import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsPath
import play.api.libs.json.Json.obj
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{TaxablePlastic, CreditsAnswer}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers

import java.time.LocalDate

class CreditsCalculationServiceSpec extends PlaySpec 
  with BeforeAndAfterEach with MockitoSugar {

  private val weightToPoundsConversionService = mock[WeightToPoundsConversionService]
  private val sut = new CreditsCalculationService(weightToPoundsConversionService)
  
  private val currentUserAnswers = UserAnswers("id")
    .setAll("obligation" -> obj { "toDate" -> "2022-06-30" })
  
  private val newUserAnswers = UserAnswers("id", obj(
    "obligation" -> obj("toDate" -> "2024-06-30"), 
    "credit" -> obj(
      "2023-04-01-2024-03-31" -> obj(
        "endDate" -> "2024-03-31",
        "exportedCredits" -> obj(
          "yesNo" -> true,
          "weight" -> 1
        ),
        "convertedCredits" -> obj(
          "yesNo" -> true,
          "weight" -> 2
        )
      ),
    )
  ))


  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(weightToPoundsConversionService)
    when(weightToPoundsConversionService.weightToCredit(any, any)) thenReturn TaxablePlastic(11, 22.3, 3.14)
  }

  // TODO split out tests covering a) CreditsAnswer, b) UserAnswers

  "totalRequestCredit" when {
    
    "given current user answers" must {

      "return the credit claimed total" in {
        sut.totalRequestedCredit(currentUserAnswers) mustBe TaxablePlastic(11, 22.3, 3.14)
      }

      "total the weight" when {

        "both answers are missing" in {
          sut.totalRequestedCredit(currentUserAnswers)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "both answers answered with false" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "exportedCredits" -> CreditsAnswer(false, None),
            "convertedCredits" -> CreditsAnswer(false, None)
          )
          sut.totalRequestedCredit(userAnswers2)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "both answers answered are only partially answered" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "exportedCredits" -> CreditsAnswer(true, None),
            "convertedCredits" -> CreditsAnswer(true, None)
          )
          sut.totalRequestedCredit(userAnswers2)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "converted is supplied" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "convertedCredits" -> CreditsAnswer(true, Some(5L))
          )
          sut.totalRequestedCredit(userAnswers2)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 5L)
        }

        "exported is supplied" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "exportedCredits" -> CreditsAnswer(true, Some(7L))
          )
          sut.totalRequestedCredit(userAnswers2)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 7L)
        }

        "both are supplied" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "convertedCredits" -> CreditsAnswer(true, Some(5L)),
            "exportedCredits" -> CreditsAnswer(true, Some(7L))
          )
          sut.totalRequestedCredit(userAnswers2)
          verify(weightToPoundsConversionService).weightToCredit(LocalDate.of(2023, 3, 31), 12L)
        }
      }

      "infer the first year end date from user answers" in {
        val userAnswers = spy(UserAnswers("user-answers-id").setAll(
          "obligation" -> obj("toDate" -> "2022-06-30")
        ))
        sut.totalRequestedCredit(userAnswers)
        
        // Previously used this
        verify(userAnswers, never).getOrFail[LocalDate](JsPath \ "obligation" \ "toDate")
        verify(weightToPoundsConversionService).weightToCredit(eqTo(LocalDate.of(2023, 3, 31)), any)
      }
    }
    
    "given new user answers" must {
      "use the correct end date for the new journey" in {
        sut.totalRequestedCredit(newUserAnswers)
        verify(weightToPoundsConversionService).weightToCredit(eqTo(LocalDate.of(2024, 3, 31)), any)
      }
      "use the correct weight for the new journey" in {
        sut.totalRequestedCredit(newUserAnswers)
        verify(weightToPoundsConversionService).weightToCredit(any, eqTo(3))
      }
      "return the correct total" in {
        sut.totalRequestedCredit(newUserAnswers) mustBe TaxablePlastic(11, 22.3, 3.14)
      }
    }
  }
}
