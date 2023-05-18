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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersCleaner.CleaningUserAnswers

class UserAnswersCleanerSpec extends PlaySpec {

  val sut = new UserAnswersCleaner

  "migrate" must {
    val to = JsPath \ "to"
    val from = JsPath \ "from"

    "move the old to the new" when {
      "the new is empty" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST")
        val result = userAnswers.migrate(from, to)

        result.get[String](to) mustBe Some("TEST")
      }
    }
    "not override existing new value with old" in {
      val userAnswers = UserAnswers("test").setOrFail(from, "TEST").setOrFail(to, "DO_NOT_OVERRIDE")
      val result = userAnswers.migrate(from, to)

      result.get[String](to) mustBe Some("DO_NOT_OVERRIDE")
    }

    "remove the old value" when {
      "it has been moved to the new" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe Some("TEST")
      }
      "the new is already populated" in {
        val userAnswers = UserAnswers("test").setOrFail(from, "TEST").setOrFail(to, "DO_NOT_OVERRIDE")
        val result = userAnswers.migrate(from, to)

        result.get[String](from) mustBe None
        result.get[String](to) mustBe Some("DO_NOT_OVERRIDE")
      }
    }
  }

}
