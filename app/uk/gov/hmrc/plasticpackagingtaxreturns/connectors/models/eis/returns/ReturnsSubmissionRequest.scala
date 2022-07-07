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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.amends.AmendValues
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.returns.ReturnValues

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
      directExports = returnValues.exportedPlasticWeight,
      recycledPlastic =  returnValues.recycledPlasticWeight,
      creditForPeriod = creditClaimed.setScale(2, RoundingMode.HALF_EVEN),
      totalWeight = calculations.chargeableTotal,
      taxDue = calculations.taxDue
    )
  }

  def apply(amendValues: AmendValues, calculations: Calculations): EisReturnDetails = {

    val creditClaimed = amendValues.convertedPackagingCredit

    returns.EisReturnDetails(
      manufacturedWeight = amendValues.manufacturedPlasticWeight,
      importedWeight = amendValues.importedPlasticWeight,
      totalNotLiable = calculations.deductionsTotal,
      humanMedicines = amendValues.humanMedicinesPlasticWeight,
      directExports = amendValues.exportedPlasticWeight,
      recycledPlastic =  amendValues.recycledPlasticWeight,
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

  def apply(returnValues: ReturnValues, calculations: Calculations, submissionId: Option[String], returnType: ReturnType): ReturnsSubmissionRequest = {

    if (returnType == (ReturnType.AMEND) && submissionId.isEmpty)
      throw new IllegalStateException("must have a submission id to amend a return")

    ReturnsSubmissionRequest(
      returnType = returnType,
      submissionId = submissionId,
      periodKey = returnValues.periodKey,
      returnDetails = EisReturnDetails(returnValues, calculations)
    )
  }

  def apply(amendValues: AmendValues, calculations: Calculations, submissionId: String, returnType: ReturnType): ReturnsSubmissionRequest = {

    ReturnsSubmissionRequest(
      returnType = returnType,
      submissionId = Some(submissionId),
      periodKey = amendValues.periodKey,
      returnDetails = EisReturnDetails(amendValues, calculations)
    )
  }

}
