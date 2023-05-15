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

import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate
import javax.inject.Inject

class AvailableCreditDateRangesService @Inject() (
  edgeOfSystem: EdgeOfSystem
) {
  
  def taxYears(date: LocalDate): Seq[Int] = {
    val isWithinNextTaxYear = date.isAfter(LocalDate.of(date.getYear, 3, 31))
    val currentTaxYearStartYear = if (isWithinNextTaxYear) date.getYear else date.getYear - 1
    val taxRegimeStartYear = 2022
    Range.inclusive(taxRegimeStartYear, currentTaxYearStartYear)
  }

  def calculate(returnEndDate: LocalDate): Seq[CreditRangeOption] = {
    // TODO base these off the quarter currently being filed
    val endDate = edgeOfSystem.today //todo use this if trimming dates
    taxYears(returnEndDate).map(toYearRange)
  }

  private def toYearRange(year: Int): CreditRangeOption = {
    CreditRangeOption(LocalDate.of(year, 4, 1), LocalDate.of(year + 1, 3, 31))
  }
}
