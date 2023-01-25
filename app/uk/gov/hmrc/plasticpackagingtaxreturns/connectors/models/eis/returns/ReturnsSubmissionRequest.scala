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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.ReturnValues

import scala.math.BigDecimal.RoundingMode

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

  def apply(returnValues: ReturnValues, calculations: Calculations): EisReturnDetails = {

    val creditClaimed = returnValues.convertedPackagingCredit

    returns.EisReturnDetails(
      manufacturedWeight = returnValues.manufacturedPlasticWeight,
      importedWeight = returnValues.importedPlasticWeight,
      totalNotLiable = calculations.deductionsTotal,
      humanMedicines = returnValues.humanMedicinesPlasticWeight,
      directExports = returnValues.totalExportedPlastic,
      recycledPlastic =  returnValues.recycledPlasticWeight,
      creditForPeriod = creditClaimed.setScale(2, RoundingMode.HALF_EVEN),
      totalWeight = calculations.chargeableTotal,
      taxDue = calculations.taxDue
    )
  }

}

case class ReturnsSubmissionRequest(
  returnType: ReturnType,
  submissionId: Option[String] = None,
  periodKey: String,
  returnDetails: EisReturnDetails,
  nrsDetails: Option[NrsDetails] = None
)

object ReturnsSubmissionRequest {

  implicit val format: OFormat[ReturnsSubmissionRequest] = Json.format[ReturnsSubmissionRequest]

  def apply(returnValues: ReturnValues, calculations: Calculations): ReturnsSubmissionRequest = {
    ReturnsSubmissionRequest(
      returnType = returnValues.returnType,
      submissionId = returnValues.submissionId,
      periodKey = returnValues.periodKey,
      returnDetails = EisReturnDetails(returnValues, calculations)
    )
  }
}
