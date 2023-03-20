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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxRate
import uk.gov.hmrc.plasticpackagingtaxreturns.services.localDateOrdering

import java.time.LocalDate

/**
 * The tax rates for PPT 
 */
class TaxRateTable {
  
  final val table = Seq(
    TaxRate(poundsPerKg = 0.20, useFromDate = LocalDate.of(2022, 4, 1)),
    TaxRate(poundsPerKg = 0.21082, useFromDate = LocalDate.of(2023, 4, 1))
  )

  /**
   * Looks up the PPT tax rate applicable to the given date
   * @param periodEndDate the last day of the period we are finding the tax-rate for
   * @return the tax rate for the given period
   */
  def lookupTaxRateForPeriod(periodEndDate: LocalDate): BigDecimal = {
    (TaxRate(0.0, LocalDate.MIN) +: table.sortBy(_.useFromDate))
      .filter(_.rateCanApplyTo(periodEndDate))
      .last
      .poundsPerKg
  }

}
