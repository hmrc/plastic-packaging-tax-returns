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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{CreditCalculation, CreditsAnswer, TaxablePlastic, UserAnswers}

import java.time.LocalDate

class CreditsCalculationServiceSpec extends PlaySpec
  with BeforeAndAfterEach with MockitoSugar {

  private val taxCalculationService = mock[TaxCalculationService]
  private val sut = new CreditsCalculationService(taxCalculationService)

  private val currentUserAnswers = UserAnswers("id", obj(
    "obligation" -> obj (
        "toDate" -> "2022-06-30"
      ),
      "whatDoYouWantToDo" -> true,
    ))

  private val newUserAnswers = UserAnswers("id", obj(
    "obligation" -> obj("toDate" -> "2024-06-30"),
    "whatDoYouWantToDo" -> true, 
    "credit" -> obj(
      "key-a" -> obj(
        "toDate" -> "2024-03-31",
        "exportedCredits" -> obj(
          "yesNo" -> true,
          "weight" -> 1
        ),
        "convertedCredits" -> obj(
          "yesNo" -> true,
          "weight" -> 2
        )
      ),
    
      "key-b" -> obj(
        "toDate" -> "2025-03-31",
        "exportedCredits" -> obj(
          "yesNo" -> true,
          "weight" -> 11
        ),
      ),
    )
  ))


  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(taxCalculationService)
    when(taxCalculationService.weightToCredit(any, any)).thenReturn(
      TaxablePlastic(1, 1.1, 1.11),
      TaxablePlastic(2, 2.2, 2.22),
    )
  }

  // TODO split out tests covering a) CreditsAnswer, b) UserAnswers

  "totalRequestCredit" when {

    "given current user answers" must {

      "return the credit claimed total" in {
        sut.totalRequestedCredit_old(currentUserAnswers)// mustBe TaxablePlastic(11, 22.3, 3.14)
        verify(taxCalculationService).weightToCredit(any, any)
      }

      "total the weight" when {

        "both answers are missing" in {
          sut.totalRequestedCredit_old(currentUserAnswers)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "both answers answered with false" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "exportedCredits" -> CreditsAnswer(false, None),
            "convertedCredits" -> CreditsAnswer(false, None)
          )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "both answers answered are only partially answered" in {
          val userAnswers2 = currentUserAnswers.setAll(
            "exportedCredits" -> CreditsAnswer(true, None),
            "convertedCredits" -> CreditsAnswer(true, None)
          )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 0L)
        }

        "converted is supplied" in {
          val userAnswers2 = currentUserAnswers
            .setOrFail(JsPath \ "whatDoYouWantToDo", true)
            .setAll("convertedCredits" -> CreditsAnswer(true, Some(5L))
            )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 5L)
        }

        "exported is supplied" in {
          val userAnswers2 = currentUserAnswers
            .setOrFail(JsPath \ "whatDoYouWantToDo", true)
            .setAll("exportedCredits" -> CreditsAnswer(true, Some(7L))
            )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 7L)
        }

        "both are supplied" in {
          val userAnswers2 = currentUserAnswers
            .setOrFail(JsPath \ "whatDoYouWantToDo", true)
            .setAll(
              "convertedCredits" -> CreditsAnswer(true, Some(5L)),
              "exportedCredits" -> CreditsAnswer(true, Some(7L))
            )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(LocalDate.of(2023, 3, 31), 12L)
        }

        "exported is false" in {
          val userAnswers2 = currentUserAnswers
            .setOrFail(JsPath \ "whatDoYouWantToDo", true)
            .setAll("exportedCredits" -> CreditsAnswer(false, Some(7L))
            )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(any, eqTo(0L))
        }
        "converted is false" in {
          val userAnswers2 = currentUserAnswers
            .setOrFail(JsPath \ "whatDoYouWantToDo", true)
            .setAll("convertedCredits" -> CreditsAnswer(false, Some(5L)),
            )
          sut.totalRequestedCredit_old(userAnswers2)
          verify(taxCalculationService).weightToCredit(any, eqTo(0L))
        }
      }

      "infer the first year end date from user answers" in {
        val userAnswers = spy(UserAnswers("user-answers-id", obj(
          "whatDoYouWantToDo" -> true,
          "obligation" -> obj("toDate" -> "2022-06-30")
        )))
        sut.totalRequestedCredit_old(userAnswers)

        // Previously used this
        verify(userAnswers, never).getOrFail[LocalDate](JsPath \ "obligation" \ "toDate")
        verify(taxCalculationService).weightToCredit(eqTo(LocalDate.of(2023, 3, 31)), any)
      }
    }

    "given new user answers" must {
      "use the correct end date for the new journey" in {
        sut.totalRequestedCredit_old(newUserAnswers)
        verify(taxCalculationService).weightToCredit(eqTo(LocalDate.of(2024, 3, 31)), any)
      }
      "use the correct weight for the new journey" in {
        sut.totalRequestedCredit_old(newUserAnswers)
        verify(taxCalculationService).weightToCredit(any, eqTo(3))
      }
      "return the correct total" in {
        sut.totalRequestedCredit_old(newUserAnswers) mustBe TaxablePlastic(1, 1.1, 1.11)
      }
    }
    
    "newJourney2" must {
      
      "calculate all years" in {
        sut.newJourney2(newUserAnswers) mustBe Map(
          ("key-a", TaxablePlastic(1, 1.1, 1.11)),
          ("key-b", TaxablePlastic(2, 2.2, 2.22))
        )
      }
      
      "do bigger object" in {
        sut.totalRequestedCredit(newUserAnswers, 11) mustBe CreditCalculation(
          availableCreditInPounds = 11, 
          totalRequestedCreditInPounds = 3.3,
          totalRequestedCreditInKilograms = 3, 
          canBeClaimed = true, 
          credit = Map(
            ("key-a", TaxablePlastic(1, 1.1, 1.11)),
            ("key-b", TaxablePlastic(2, 2.2, 2.22))
        ))
      }
    }
    
    "there is no credit claim" in {}
    "there is claim data, but user has said no to claiming" in {}
    
  }
}
