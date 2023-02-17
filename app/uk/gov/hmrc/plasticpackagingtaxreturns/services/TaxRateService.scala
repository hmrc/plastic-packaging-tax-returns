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
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.TaxRate


import java.time.LocalDate

class TaxRateService  @Inject()(appConfig: AppConfig) {

  /** When the tax rate changes, add another if-test here
   *
   * @param periodEndDate the last day of the period we are finding the tax-rate for
   * @return the tax rate for the given period
   */
  def lookupTaxRateForPeriod(periodEndDate: LocalDate): BigDecimal = {
    (TaxRate(0.0, LocalDate.MIN) +: appConfig.taxRatesPoundsPerKg)
      .filter(_.rateCanApplyTo(periodEndDate))
      .last
      .rate
  }
}
