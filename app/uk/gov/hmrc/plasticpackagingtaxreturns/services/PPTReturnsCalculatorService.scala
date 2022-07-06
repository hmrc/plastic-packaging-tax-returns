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

import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{Calculations, ReturnValues}

import javax.inject.Inject
import scala.math.BigDecimal.RoundingMode

class PPTReturnsCalculatorService @Inject() (appConfig: AppConfig) {

  def calculate(returnValues: ReturnValues): Calculations = {

    val packagingTotal: Long = returnValues.importedPlasticWeight +
      returnValues.manufacturedPlasticWeight

    val deductionsTotal: Long = returnValues.exportedPlasticWeight +
      returnValues.humanMedicinesPlasticWeight +
      returnValues.recycledPlasticWeight

    val chargeableTotal: Long = packagingTotal - deductionsTotal // TODO - credits!

    // Round in favour of the customer (DOWN)
    val taxDue: BigDecimal = (BigDecimal(chargeableTotal) * appConfig.taxRatePoundsPerKg).setScale(2, RoundingMode.DOWN) // Verify with BA!

    val isSubmittable: Boolean = {
      returnValues.manufacturedPlasticWeight >= 0 &&
        returnValues.importedPlasticWeight >= 0 &&
        returnValues.exportedPlasticWeight >= 0 &&
        returnValues.recycledPlasticWeight >= 0 &&
        returnValues.humanMedicinesPlasticWeight >= 0 &&
        packagingTotal >= deductionsTotal &&
        chargeableTotal >= 0
    }

    Calculations(
      taxDue,
      chargeableTotal,
      deductionsTotal,
      packagingTotal,
      isSubmittable)
  }


}
