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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import play.api.Logging
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.ReturnValues

import javax.inject.Inject

class PPTCalculationService @Inject()(
                                     creditsCalculationService: CreditsCalculationService,
                                     conversionService: WeightToPoundsConversionService
                                     ) extends Logging {

  def calculate(returnValues: ReturnValues): Calculations =
    doCalculation(
      returnValues.importedPlasticWeight,
      returnValues.manufacturedPlasticWeight,
      returnValues.humanMedicinesPlasticWeight,
      returnValues.recycledPlasticWeight,
      returnValues.exportedPlasticWeight,
      returnValues.convertedPackagingCredit,
      returnValues.availableCredit
    )

  private def doCalculation(
                            importedPlasticWeight: Long,
                            manufacturedPlasticWeight: Long,
                            humanMedicinesPlasticWeight: Long,
                            recycledPlasticWeight: Long,
                            exportedPlasticWeight: Long,
                            convertedPackagingCredit: BigDecimal,
                            availableCredit: BigDecimal
                          ): Calculations = {

    val packagingTotal: Long = importedPlasticWeight +
      manufacturedPlasticWeight

    val deductionsTotal: Long = exportedPlasticWeight +
      humanMedicinesPlasticWeight +
      recycledPlasticWeight

    val chargeableTotal: Long = scala.math.max(0, packagingTotal - deductionsTotal)

    val taxDue: BigDecimal = conversionService.weightToDebit(chargeableTotal)

    val isSubmittable: Boolean = {
      manufacturedPlasticWeight >= 0 &&
        importedPlasticWeight >= 0 &&
        exportedPlasticWeight >= 0 &&
        recycledPlasticWeight >= 0 &&
        humanMedicinesPlasticWeight >= 0 &&
        packagingTotal >= deductionsTotal &&
        chargeableTotal >= 0 &&
        convertedPackagingCredit <= availableCredit
    }

    if(!isSubmittable) {
      logger.info("PPT return not submittable")
    }

    Calculations(
      taxDue,
      chargeableTotal,
      deductionsTotal,
      packagingTotal,
      convertedPackagingCredit,
      isSubmittable)
  }

}
