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

import play.api.libs.json.{JsValue, Json, Writes}

final case class CreditsCalculationResponse(
                                             availableCreditInPounds: BigDecimal,
                                             totalRequestedCreditInPounds: BigDecimal,
                                             totalRequestedCreditInKilograms: Long,
                                             taxRate: Seq[TaxRate]
                                           ){
  val canBeClaimed: Boolean = totalRequestedCreditInPounds <= availableCreditInPounds
}

object CreditsCalculationResponse {

  implicit val writes: Writes[CreditsCalculationResponse] = new Writes[CreditsCalculationResponse] {
    def writes(calc: CreditsCalculationResponse): JsValue = {
      Json.obj(
        "availableCreditInPounds" -> calc.availableCreditInPounds,
        "totalRequestedCreditInPounds" -> calc.totalRequestedCreditInPounds,
        "totalRequestedCreditInKilograms" -> calc.totalRequestedCreditInKilograms,
        "canBeClaimed" -> calc.canBeClaimed,
        "taxRate" -> calc.taxRate
      )
    }
  }

}
