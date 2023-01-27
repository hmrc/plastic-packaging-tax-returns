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
import uk.gov.hmrc.plasticpackagingtaxreturns.services.TaxRateService.TaxRatesAndUseFrom

import java.time.LocalDate

class TaxRateService  @Inject()(appConfig: AppConfig) {

  /** When the tax rate changes, add another if-test here
   *
   * @param periodEndDate the last day of the period we are finding the tax-rate for
   * @return the tax rate for the given period
   */
  def lookupTaxRateForPeriod(periodEndDate: LocalDate): Double = {
    if (periodEndDate.isBefore(appConfig.taxRegimeStartDate))
      appConfig.taxRateBefore1stApril2022
    else
      appConfig.taxRateFrom1stApril2022
  }


  // thoughts? //todo not yes in use
  private def get(localDate: LocalDate): Double =
    TaxRatesAndUseFrom
      .filter(_.rateCanApplyTo(localDate))
      .last //most recent
      .rate

}

object TaxRateService {

  //this could be read from application conf too if we really want
  private val TaxRatesAndUseFrom: Seq[TaxRate] = Seq( //something like:
      TaxRate(0, LocalDate.MIN),
      TaxRate(0.2, LocalDate.of(2022, 4, 1)),
      TaxRate(0.5, LocalDate.of(2023, 4, 1))
    ).sortBy(_.useFromDate)


  private case class TaxRate(rate: Double, useFromDate: LocalDate) {
    def rateCanApplyTo(localDate: LocalDate): Boolean =
      useFromDate.isBefore(localDate) || useFromDate.isEqual(localDate)
  }
}
