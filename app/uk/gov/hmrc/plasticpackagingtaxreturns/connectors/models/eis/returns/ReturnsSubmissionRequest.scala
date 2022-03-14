/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{ReturnType, TaxReturn}

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

  def apply(taxReturn: TaxReturn, taxRatePoundsPerKg: BigDecimal): EisReturnDetails = {
    val manufacturedWeightKg = taxReturn.manufacturedPlasticWeight.map(_.totalKg).getOrElse(0L)
    val importedWeightKg     = taxReturn.importedPlasticWeight.map(_.totalKg).getOrElse(0L)
    val liableKg             = manufacturedWeightKg + importedWeightKg

    val humanMedicinesWeightKg = taxReturn.humanMedicinesPlasticWeight.map(_.totalKg).getOrElse(0L)
    val directExportsWeightKg  = taxReturn.exportedPlasticWeight.map(_.totalKg).getOrElse(0L)
    val recycledWeightKg       = taxReturn.recycledPlasticWeight.map(_.totalKg).getOrElse(0L)
    val notLiableKg            = humanMedicinesWeightKg + directExportsWeightKg + recycledWeightKg

    val taxableKg = Math.max(liableKg - notLiableKg, 0)

    returns.EisReturnDetails(manufacturedWeight = manufacturedWeightKg,
                             importedWeight = importedWeightKg,
                             totalNotLiable = notLiableKg,
                             humanMedicines = humanMedicinesWeightKg,
                             directExports = directExportsWeightKg,
                             recycledPlastic = recycledWeightKg,
                             creditForPeriod = 0,
                             totalWeight = taxableKg,
                             taxDue = BigDecimal(taxableKg) * taxRatePoundsPerKg
    )
  }

}

case class ReturnsSubmissionRequest(
  returnType: ReturnType,
  submissionId: Option[String] = None,
  periodKey: String,
  returnDetails: EisReturnDetails
)

object ReturnsSubmissionRequest {
  implicit val format: OFormat[ReturnsSubmissionRequest] = Json.format[ReturnsSubmissionRequest]

  def apply(taxReturn: TaxReturn, taxRatePoundsPerKg: BigDecimal): ReturnsSubmissionRequest =
    ReturnsSubmissionRequest(returnType = taxReturn.returnType.getOrElse(ReturnType.NEW),
                             periodKey = taxReturn.obligation.map(_.periodKey).getOrElse(
                               throw new IllegalStateException("Obligation is absent")
                             ),
                             returnDetails = EisReturnDetails(taxReturn, taxRatePoundsPerKg: BigDecimal)
    )

}
