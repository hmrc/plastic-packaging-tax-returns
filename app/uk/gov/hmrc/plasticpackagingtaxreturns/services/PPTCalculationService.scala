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

import play.api.Logging
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.ReturnValues

import java.time.LocalDate
import javax.inject.Inject

class PPTCalculationService @Inject() (taxCalculationService: TaxCalculationService) extends Logging {

  def calculate(returnValues: ReturnValues): Calculations =
    doCalculation(
      returnValues.periodEndDate,
      returnValues.importedPlasticWeight,
      returnValues.manufacturedPlasticWeight,
      returnValues.humanMedicinesPlasticWeight,
      returnValues.recycledPlasticWeight,
      returnValues.totalExportedPlastic,
      returnValues.convertedPackagingCredit,
      returnValues.availableCredit
    )

  private def doCalculation(
    periodEndDate: LocalDate,
    importedPlasticWeight: Long,
    manufacturedPlasticWeight: Long,
    humanMedicinesPlasticWeight: Long,
    recycledPlasticWeight: Long,
    totalExportedPlasticWeight: Long,
    convertedPackagingCredit: BigDecimal,
    availableCredit: BigDecimal
  ): Calculations = {

    val packagingTotal: Long  = importedPlasticWeight + manufacturedPlasticWeight
    val deductionsTotal: Long = totalExportedPlasticWeight + humanMedicinesPlasticWeight + recycledPlasticWeight
    val chargeableTotal: Long = scala.math.max(0, packagingTotal - deductionsTotal)
    val taxPayable            = taxCalculationService.weightToDebit(periodEndDate, chargeableTotal)

    val submittableEval = Either.cond(
      manufacturedPlasticWeight >= 0,
      true,
      "manufactured plastic weight lte zero"
    )
      .flatMap(_ => Either.cond(importedPlasticWeight >= 0, true, "imported plastic weight lte zero"))
      .flatMap(_ =>
        Either.cond(totalExportedPlasticWeight >= 0, true, "total exported plastic weight lte zero")
      )
      .flatMap(_ => Either.cond(recycledPlasticWeight >= 0, true, "recycled plastic weight lte zero"))
      .flatMap(_ =>
        Either.cond(humanMedicinesPlasticWeight >= 0, true, "human medicines plastic weight lte zero")
      )
      .flatMap(_ =>
        Either.cond(packagingTotal >= deductionsTotal, true, "deductions total greater than packaging total")
      )
      .flatMap(_ => Either.cond(chargeableTotal >= 0, true, "chargeable total lte zero"))
      .flatMap(_ =>
        Either.cond(
          convertedPackagingCredit <= availableCredit,
          true,
          "available credit greater than converted packaging credit"
        )
      )

    val isSubmittable = submittableEval match {
      case Left(err)   => logger.warn(err); false
      case Right(bool) => bool
    }

    Calculations(
      taxPayable.moneyInPounds,
      chargeableTotal,
      deductionsTotal,
      packagingTotal,
      isSubmittable,
      taxPayable.taxRate
    )
  }

}
