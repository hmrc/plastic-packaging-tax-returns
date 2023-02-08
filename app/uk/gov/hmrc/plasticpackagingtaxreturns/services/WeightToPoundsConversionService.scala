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


import com.google.inject.Inject

import java.time.LocalDate
import scala.math.BigDecimal.RoundingMode

/*
  Policy have advised that Rounding must always happen in favor of the customer.
  When Calculating a credit (money given to the customer) round up.
  When calculating a debit (money taken from the customer) round down.

  While the tax rate conversion is currently 0.2 this rounding does not take affect,
   however if it were ever more than 2 d.p. this would make a difference so it is
   important to use the correct conversion method
 */

class WeightToPoundsConversionService @Inject()(taxRateService: TaxRateService) {

  /**
   * Calculates the tax payable for the given weight (kg) and quarter. The tax rate used in the calculation is the
   * tax rate for that quarter. Tax rates are in £ per kg, and weights are in kg.
   * @param periodEndDate date tax period period ended, eg for 22C2 the end-date would be 30th June 2022 
   * @param weightInKg total weight in kg to pay tax on
   * @return the amount (in £) of tax payable, rounded-down to nearest pence
   */
  def weightToDebit(periodEndDate: LocalDate, weightInKg: Long): BigDecimal = {
    val taxRateForPeriod = taxRateService.lookupTaxRateForPeriod(periodEndDate)
    val currency = BigDecimal(weightInKg) * taxRateForPeriod // tax rate is £ per kg
    currency.setScale(2, RoundingMode.DOWN)
  }

  // TODO allow for period / rate look up somehow
  def weightToCredit(weight: Long): BigDecimal = {
    val currency = BigDecimal(weight) * taxRateService.lookupTaxRateForPeriod(LocalDate.now()) //todo: credits need to bring in a date
    currency.setScale(2, RoundingMode.UP)
  }

}
