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

import play.api.libs.json.{JsPath, JsValue}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers


/** A cleaner that formats user answers from old JsPaths
 * to maintain backwards compatibility for users midjourney during deployments
 */

class UserAnswersCleaner {

  def clean(userAnswers: UserAnswers): UserAnswers = {
    ???
  }

}

object UserAnswersCleaner {

  implicit class CleaningUserAnswers(val userAnswers: UserAnswers) extends AnyVal {
    def migrate(from: JsPath, to: JsPath): UserAnswers =
      (if (userAnswers.get[JsValue](to).isDefined) userAnswers
      else userAnswers.setOrFail(to, userAnswers.get[JsValue](from))
        ).removePath(from)
  }
}
