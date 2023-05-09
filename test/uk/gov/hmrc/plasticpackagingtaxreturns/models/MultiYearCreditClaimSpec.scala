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
import play.api.libs.json.{JsPath, Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers

import java.time.LocalDate

case class SingleYearClaim(endDate: LocalDate, exportedCredits: Option[CreditsAnswer], convertedCredits: Option[CreditsAnswer])

object SingleYearClaim {
  
  def readFirstFrom(userAnswers: UserAnswers): SingleYearClaim =
    userAnswers
      .getOrFail[Map[String, SingleYearClaim]](JsPath \ "credit")
      .values
      .head

  implicit val formats: OFormat[SingleYearClaim] = Json.format[SingleYearClaim]
}

class MultiYearCreditClaimSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {
  
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
  }
}
