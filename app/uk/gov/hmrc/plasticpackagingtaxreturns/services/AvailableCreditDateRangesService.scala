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
import scala.annotation.tailrec

class AvailableCreditDateRangesService @Inject() (
  edgeOfSystem: EdgeOfSystem
) {

  def taxYears(date: LocalDate): Seq[(LocalDate, LocalDate)] = {
    TaxQuarter(date)
      .previousEightQuarters
      .groupBy(_.taxYear)
      .values.toSeq
      .map{ range =>
        range.minBy(_.quarter).fromToDates._1 -> range.maxBy(_.quarter).fromToDates._2
      }
  }


  def calculate(returnEndDate: LocalDate): Seq[CreditRangeOption] = {
    val endDate = edgeOfSystem.today //todo use this if trimming from 'todays date'

    val startDate = LocalDate.of(2022, 4, 1) //todo use user's taxStartDate
    taxYears(returnEndDate).collect{
      case (from, to) if from.isAfter(startDate) => from -> to
      case (from, to) if from.isEqual(startDate) => from -> to
      case (from, to) if to.isEqual(startDate) => from -> to
      case (from, to) if from.isBefore(startDate) && to.isAfter(startDate) => startDate -> to
    }.map(CreditRangeOption.apply)
  }

}

object AvailableCreditDateRangesService {

  final case class TaxQuarter(taxYear: Int, quarter: Int) {
    require(quarter >= 1 && quarter <= 4) //todo

    private def previousQuarter = if (quarter == 1) TaxQuarter(taxYear-1, 4) else TaxQuarter(taxYear, quarter -1)

    def previousEightQuarters: Seq[TaxQuarter] = {
      @tailrec
      def builder(seq: Seq[TaxQuarter]): Seq[TaxQuarter] = {
        if (seq.size == 8) seq
        else builder(seq :+ seq.last.previousQuarter)
      }
      builder(Seq(previousQuarter))
    }

    private def firstOf(month: Month, year: Int): LocalDate = LocalDate.of(year, month, 1)
    private def endOf(month: Month, year: Int): LocalDate =  LocalDate.of(year, month, month.length(true))

    def fromToDates: (LocalDate, LocalDate) = quarter match {
      case 1 => firstOf(APRIL, taxYear) -> endOf(JUNE, taxYear)
      case 2 => firstOf(JULY, taxYear) -> endOf(SEPTEMBER, taxYear)
      case 3 => firstOf(OCTOBER, taxYear) -> endOf(DECEMBER, taxYear)
      case 4 => firstOf(JANUARY, taxYear + 1) -> endOf(MARCH, taxYear + 1)
      case e => throw new IllegalStateException(s"quarter must be 1-4 was $e")
    }
  }

  object TaxQuarter {
    def apply(localDate: LocalDate): TaxQuarter = {
      val year = localDate.getYear
      localDate.getMonth match {
        case APRIL | MAY | JUNE => new TaxQuarter(year, 1)
        case JULY | AUGUST | SEPTEMBER => new TaxQuarter(year, 2)
        case OCTOBER | NOVEMBER | DECEMBER => new TaxQuarter(year, 3)
        case JANUARY | FEBRUARY | MARCH => new TaxQuarter(year - 1, 4)
      }
    }
  }
}
