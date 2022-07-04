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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn

import scala.math.BigDecimal.RoundingMode

class PPTReturnsCalculatorService(appConfig: AppConfig) {

  def calculate(taxReturn: TaxReturn): Calculations = {

    val packagingTotal: Long = taxReturn.importedPlasticWeight.totalKg +
      taxReturn.manufacturedPlasticWeight.totalKg

    val deductionsTotal: Long = taxReturn.exportedPlasticWeight.totalKg +
      taxReturn.humanMedicinesPlasticWeight.totalKg +
      taxReturn.recycledPlasticWeight.totalKg

    val chargeableTotal: Long = packagingTotal - deductionsTotal

    val taxDue: BigDecimal = (BigDecimal(chargeableTotal) * appConfig.taxRatePoundsPerKg).setScale(2, RoundingMode.HALF_EVEN)

    Calculations(
      taxDue,
      chargeableTotal,
      deductionsTotal,
      packagingTotal,
      false)
  }


}
