/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends.ReturnDisplayApi

case class AmendsCalculations(original: Calculations, amend: Calculations)

object AmendsCalculations {
  implicit val format: OFormat[AmendsCalculations] = Json.format[AmendsCalculations]
}

case class Calculations(
  taxDue: BigDecimal,
  chargeableTotal: Long,
  deductionsTotal: Long,
  packagingTotal: Long,
  isSubmittable: Boolean,
  taxRate: BigDecimal
)

object Calculations {
  implicit val format: OFormat[Calculations] = Json.format[Calculations]

  def fromReturn(returnDisplayApi: ReturnDisplayApi, taxRate: BigDecimal): Calculations =
    new Calculations(
      returnDisplayApi.returnDetails.taxDue,
      returnDisplayApi.returnDetails.totalWeight,
      returnDisplayApi.returnDetails.totalNotLiable,
      returnDisplayApi.returnDetails.manufacturedWeight + returnDisplayApi.returnDetails.importedWeight,
      true,
      taxRate
    )

}
