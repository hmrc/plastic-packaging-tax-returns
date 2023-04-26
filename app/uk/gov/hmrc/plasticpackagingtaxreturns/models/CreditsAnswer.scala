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

import play.api.libs.json.{JsPath, Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers

// TODO add test coverage

case class CreditsAnswer(yesNo: Boolean, weight: Option[Long]) {
  def value: Long = (yesNo, weight) match {
    case (true, Some(x)) => x
    case _ => 0
  }
}

object CreditsAnswer {
  def noClaim: CreditsAnswer = CreditsAnswer(false, None)

  implicit val formats: OFormat[CreditsAnswer] = Json.format[CreditsAnswer]

  def readFrom(userAnswers: UserAnswers, path: String): CreditsAnswer = {
    userAnswers
      .get[CreditsAnswer](JsPath \ path)
      .getOrElse(CreditsAnswer.noClaim) // therefore will be (no and zero) if missing / unanswered 
  }
}
