/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat}

case class EisReturnDetails(
  manufacturedWeight: BigDecimal,
  importedWeight: BigDecimal,
  totalNotLiable: BigDecimal,
  humanMedicines: BigDecimal,
  directExports: BigDecimal,
  recycledPlastic: BigDecimal,
  creditForPeriod: BigDecimal,
  totalWeight: BigDecimal,
  taxDue: BigDecimal
)

object EisReturnDetails {
  implicit val format: OFormat[EisReturnDetails] = Json.format[EisReturnDetails]

  def fromTaxReturn(taxReturn: TaxReturn) =
    EisReturnDetails(
      manufacturedWeight = taxReturn.manufacturedPlasticWeight match {
        case Some(weight) => weight.totalKg
        case _            => 0
      },
      importedWeight = taxReturn.importedPlasticWeight match {
        case Some(weight) => weight.totalKg
        case _            => 0
      },
      totalNotLiable = 0,
      humanMedicines = taxReturn.humanMedicinesPlasticWeight match {
        case Some(weight) => weight.totalKg
        case _            => 0
      },
      directExports = taxReturn.exportedPlasticWeight match {
        case Some(weight) => weight.totalKg
        case _            => 0
      },
      recycledPlastic = taxReturn.recycledPlasticWeight match {
        case Some(weight) => weight.totalKg
        case _            => 0
      },
      creditForPeriod = 0,
      totalWeight = 0,
      taxDue = 0
    )

}

case class EisReturnCreateUpdateRequest(
  returnType: String,
  submissionId: Option[String] = None,
  periodKey: String,
  returnDetails: EisReturnDetails
)

object EisReturnCreateUpdateRequest {
  implicit val format: OFormat[EisReturnCreateUpdateRequest] = Json.format[EisReturnCreateUpdateRequest]

  def fromTaxReturn(taxReturn: TaxReturn) =
    EisReturnCreateUpdateRequest(returnType = "New",
                                 periodKey = "TODO",
                                 returnDetails = EisReturnDetails.fromTaxReturn(taxReturn)
    )

}
