/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxablePlastic
import uk.gov.hmrc.plasticpackagingtaxreturns.util.TaxRateTable

import java.time.LocalDate
import scala.math.BigDecimal.RoundingMode

/** Calculate the tax to be paid, or the credit to be claimed for a given weight of taxable packaging
  * @param taxRateTable
  *   auto-injected PPT tax rate table
  * @note
  *   Policy have advised that rounding must always happen in favor of the customer:
  *   - when calculating a credit (money given to the customer) round up
  *   - when calculating a debit (money taken from the customer) round down
  */
class TaxCalculationService @Inject() (taxRateTable: TaxRateTable) {

  /** Calculates the tax payable for the given weight (kg) and quarter. The tax rate used in the calculation is the tax
    * rate for that quarter. Tax rates are in £ per kg, and weights are in kg.
    * @param periodEndDate
    *   date tax period period ended, eg for 22C2 the end-date would be 30th June 2022
    * @param weightInKg
    *   total weight in kg to pay tax on
    * @return
    *   the amount (in £) of tax payable, rounded-down to nearest pence
    */
  def weightToDebit(periodEndDate: LocalDate, weightInKg: Long): TaxablePlastic = {
    val taxRateApplied = taxRateTable.lookupRateFor(periodEndDate)
    val currency       = BigDecimal(weightInKg) * taxRateApplied // tax rate is £ per kg
    val moneyInPounds  = currency.setScale(2, RoundingMode.DOWN)
    TaxablePlastic(weightInKg, moneyInPounds, taxRateApplied)
  }

  /** Calculates credit amount for the given weight, ie tax previously due / paid, using the relevant tax rate applied
    * at that time. Tax rates are in £ per kg, and weights are in kg.
    * @param taxRateEndDate
    *   reference to tax-rate applied at the time tax was paid. As an example, for tax paid during 2022-23, end date
    *   would be 2023-03-31
    * @param weightInKg
    *   weight of plastic to claim credit for, in kg
    * @return
    *   the amount in £ this credit claim is worth, rounded up to the nearest pence
    */
  def weightToCredit(taxRateEndDate: LocalDate, weightInKg: Long): TaxablePlastic = {
    val taxRateApplied = taxRateTable.lookupRateFor(taxRateEndDate)
    val currency       = BigDecimal(weightInKg) * taxRateApplied
    val moneyInPounds  = currency.setScale(2, RoundingMode.UP)
    TaxablePlastic(weightInKg, moneyInPounds, taxRateApplied)
  }

}
