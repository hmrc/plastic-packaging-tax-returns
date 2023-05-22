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

import play.api.libs.json.{JsObject, JsPath, JsValue, Writes}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersCleaner.CleaningUserAnswers

import java.time.LocalDate
import javax.inject.Inject


/** A cleaner that formats user answers from old JsPaths
 * to maintain backwards compatibility for users mid journey during deployments
 */

class UserAnswersCleaner @Inject()(
                                  availableCreditDateRangesService: AvailableCreditDateRangesService
                                  ){

  private def getAssumedDateRange(userAnswers: UserAnswers) = {
    val returnToDate = userAnswers.getOrFail[LocalDate](JsPath \ "obligation" \ "toDate")
    val available = availableCreditDateRangesService.calculate(returnToDate)
    available match {
      case Seq(claimPeriod) => claimPeriod
      case e => throw new IllegalStateException(s"Cannot assume tax year for existing credits as ${e.size} are available")
    }
  }

  def clean(userAnswers: UserAnswers): (UserAnswers, Boolean) = {

    if (!userAnswers.get[JsObject](JsPath \ "exportedCredits").isDefined) {
      userAnswers -> false
    } else {
      val taxRange = getAssumedDateRange(userAnswers: UserAnswers)
      userAnswers
        .migrate(JsPath \ "exportedCredits" \ "yesNo", JsPath \ "credit" \ taxRange.key \ "exportedCredits" \ "yesNo")
        .migrate(JsPath \ "exportedCredits" \ "weight", JsPath \ "credit" \ taxRange.key \ "exportedCredits" \ "weight")
        .migrate(JsPath \ "convertedCredits" \ "yesNo", JsPath \ "credit" \ taxRange.key \ "convertedCredits" \ "yesNo")
        .migrate(JsPath \ "convertedCredits" \ "weight", JsPath \ "credit" \ taxRange.key \ "convertedCredits" \ "weight")
        .removePath(JsPath \ "exportedCredits")
        .removePath(JsPath \ "convertedCredits")
        .setOrFail(JsPath \ "credit" \ taxRange.key \ "endDate", taxRange.to) -> true
    }
  }

}

object UserAnswersCleaner {

  implicit class CleaningUserAnswers(val userAnswers: UserAnswers) extends AnyVal {
    def migrate(from: JsPath, to: JsPath): UserAnswers =
      (if (userAnswers.get[JsValue](to).isDefined) userAnswers
      else userAnswers.setOrFail(to, userAnswers.get[JsValue](from))
        ).removePath(from)

    def setIfDefined[A](check: JsPath, setPath: JsPath, value: A)(implicit writes: Writes[A]): UserAnswers = {
      if (userAnswers.get[JsValue](check).isDefined) userAnswers.setOrFail(setPath, value)
      else userAnswers
    }
  }
}
