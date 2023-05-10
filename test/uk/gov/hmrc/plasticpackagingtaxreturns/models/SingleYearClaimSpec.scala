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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json.obj

import java.time.LocalDate

class SingleYearClaimSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {
  
  private val exampleClaim = SingleYearClaim(
    LocalDate.of(2024, 3, 31),
    Some(CreditsAnswer(true, Some(1L))),
    Some(CreditsAnswer(true, Some(2L))),
  )
  
  "it" should {
    
    "read from user answers" in {
      val userAnswers = UserAnswers("id", obj(
        "credit" -> obj(
          "2023-04-01-2024-03-31" -> obj(
            "endDate" -> "2024-03-31",
            "exportedCredits" -> obj(
              "yesNo" -> true,
              "weight" -> 12344
            ),
            "convertedCredits" -> obj(
              "yesNo" -> true,
              "weight" -> 123
            )
          ),
        )
      ))

      SingleYearClaim.readFirstFrom(userAnswers) mustBe SingleYearClaim(
        LocalDate.of(2024, 3, 31),
        Some(CreditsAnswer(true, Some(12344L))),
        Some(CreditsAnswer(true, Some(123L))),
      )
    }
    
    "total the weight of the claim" in {
//      exampleClaim.totalWeight mustBe 3L
    }
    
  }
}
