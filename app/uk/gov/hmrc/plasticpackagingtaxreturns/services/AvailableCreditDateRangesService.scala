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
import uk.gov.hmrc.plasticpackagingtaxreturns.services.AvailableCreditDateRangesService.TaxQuarter
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.{LocalDate, Month}
import java.time.Month._
import javax.inject.Inject

class AvailableCreditDateRangesService @Inject() (
  edgeOfSystem: EdgeOfSystem
) {

  def taxYears(date: LocalDate): Seq[(LocalDate, LocalDate)] = {
    TaxQuarter(date)
      .previousEightQuarters
      .groupBy(_.taxYear)
      .values.toSeq
      .map(range => range.minBy(_.fromDate).fromDate -> range.maxBy(_.toDate).toDate)
  }

  def calculate(returnEndDate: LocalDate, taxStartDate: LocalDate = LocalDate.of(2022, 4, 1)): Seq[CreditRangeOption] = {
    taxYears(returnEndDate).collect{
      case (from, to) if from.isAfter(taxStartDate) => from -> to
      case (from, to) if from.isEqual(taxStartDate) => from -> to
      case (from, to) if to.isEqual(taxStartDate) => from -> to
      case (from, to) if from.isBefore(taxStartDate) && to.isAfter(taxStartDate) => taxStartDate -> to
    }.map(CreditRangeOption.apply)
  }

}

object AvailableCreditDateRangesService {

  sealed abstract class TaxQuarter(val startMonth: Month, val endMonth: Month, previous: Int => TaxQuarter, previousQuarterYearOffset: Int = 0, fromDatesYearOffset: Int = 0) {
    val taxYear: Int
    def fromDate: LocalDate = LocalDate.of(taxYear + fromDatesYearOffset, startMonth, 1)
    def toDate: LocalDate = LocalDate.of(taxYear + fromDatesYearOffset, endMonth, endMonth.length(true))
    def previousQuarter: TaxQuarter = previous.apply(taxYear + previousQuarterYearOffset)
    def previousEightQuarters: Seq[TaxQuarter] =
      (1 until 8).foldLeft(Seq(previousQuarter))((acc, _) => acc :+ acc.last.previousQuarter)
  }

  object TaxQuarter {
    final case class Q1(taxYear: Int) extends TaxQuarter(APRIL, JUNE, previous = Q4, previousQuarterYearOffset = -1)
    final case class Q2(taxYear: Int) extends TaxQuarter(JULY, SEPTEMBER, previous = Q1)
    final case class Q3(taxYear: Int) extends TaxQuarter(OCTOBER, DECEMBER, previous = Q2)
    final case class Q4(taxYear: Int) extends TaxQuarter(JANUARY, MARCH, previous = Q3, fromDatesYearOffset = 1)

    def apply(localDate: LocalDate): TaxQuarter = {
      val year = localDate.getYear
      localDate.getMonth match {
        case APRIL | MAY | JUNE => Q1(taxYear = year)
        case JULY | AUGUST | SEPTEMBER => Q2(taxYear = year)
        case OCTOBER | NOVEMBER | DECEMBER => Q3(taxYear = year)
        case JANUARY | FEBRUARY | MARCH => Q4(taxYear = year-1)
      }
    }
  }
}
