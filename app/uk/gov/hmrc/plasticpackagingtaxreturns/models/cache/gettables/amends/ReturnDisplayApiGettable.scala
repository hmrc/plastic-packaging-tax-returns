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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends

import play.api.libs.json.{JsPath, Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.Gettable

final case class ReturnDisplayDetails(
                                 manufacturedWeight: Long,
                                 importedWeight: Long,
                                 totalNotLiable: Long,
                                 humanMedicines: Long,
                                 directExports: Long,
                                 recycledPlastic: Long,
                                 creditForPeriod: BigDecimal,
                                 debitForPeriod: BigDecimal,
                                 totalWeight: Long,
                                 taxDue: BigDecimal
                               )

object ReturnDisplayDetails {
  implicit val format: OFormat[ReturnDisplayDetails] = Json.format[ReturnDisplayDetails]
}

case class IdDetails(pptReferenceNumber: String, submissionId: String)

object IdDetails {
  implicit val format: OFormat[IdDetails] = Json.format[IdDetails]
}

final case class ReturnDisplayApi(
                             returnDetails: ReturnDisplayDetails,
                             idDetails: IdDetails
                           )

object ReturnDisplayApi {
  implicit val format: OFormat[ReturnDisplayApi] = Json.format[ReturnDisplayApi]
}

case object ReturnDisplayApiGettable extends Gettable[ReturnDisplayApi] {

  override def path: JsPath = JsPath \ toString

  override def toString: String = "returnDisplayApi"

}