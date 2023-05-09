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
import play.api.libs.json.{JsPath, Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers

import java.time.LocalDate

case class SingleYearClaim(endDate: LocalDate, exportedCredits: CreditsAnswer, convertedCredits: CreditsAnswer)

object SingleYearClaim {
  implicit val formats: OFormat[SingleYearClaim] = Json.format[SingleYearClaim]

}

class MultiYearCreditClaimSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {
  private val asString =
    """
    {
      "_id" : "Int-165fb8fa-20fe-419c-8c17-3f142f36423c-XMPPT0000000003",
      "data" : {
          "obligation" : {
              "fromDate" : "2023-01-01",
              "toDate" : "2023-03-31",
              "dueDate" : "2023-05-31",
              "periodKey" : "23C1"
          },
          "isFirstReturn" : false,
          "startYourReturn" : true,
          "whatDoYouWantToDo" : true,
          "credit" : {
              "2023-04-01-2024-03-31" : {
                  "endDate" : "2024-03-31",
                  "exportedCredits" : {
                      "yesNo" : true,
                      "weight" : 12344
                  },
                  "convertedCredits" : {
                      "yesNo" : true,
                      "weight" : 123
                  }
              },
              "2023-01-01-2023-03-31" : {
                  "endDate" : "2023-03-31",
                  "exportedCredits" : {
                      "yesNo" : false
                  },
                  "convertedCredits" : {
                      "yesNo" : true,
                      "weight" : 99
                  }
              }
          }
      },
      "lastUpdated" : {"$date":{"$numberLong":"0"}}
    }
    """
    
  "it should read from json via UserAnswers" in {
    val userAnswers = Json.parse(asString).as[UserAnswers]
    userAnswers.getOrFail[Map[String, SingleYearClaim]](JsPath \ "credit").values.head mustBe SingleYearClaim(
      LocalDate.of(2024, 3, 31),
      CreditsAnswer(true, Some(12344L)), 
      CreditsAnswer(true, Some(123L)), 
    )
  }
}
