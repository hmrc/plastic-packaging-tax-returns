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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.returns

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

final case class CreditRangeOption(from: LocalDate, to: LocalDate)

object CreditRangeOption {
  implicit val format: OFormat[CreditRangeOption] = Json.format[CreditRangeOption]

  def apply(fromTo: (LocalDate, LocalDate)): CreditRangeOption =
    new CreditRangeOption(fromTo._1, fromTo._2)
}
