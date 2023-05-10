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

import play.api.libs.json.{Json, OWrites}

import java.time.LocalDate

/**
 * @param poundsPerKg - tax rate in £ per kg
 * @param useFromDate - earliest day from which this rate applies
 */
case class TaxRate(poundsPerKg: BigDecimal, useFromDate: LocalDate) {
  def rateCanApplyTo(localDate: LocalDate): Boolean =
    useFromDate.isBefore(localDate) || useFromDate.isEqual(localDate)
}

object TaxRate {
  implicit val formats: OWrites[TaxRate] = Json.writes[TaxRate]
}
