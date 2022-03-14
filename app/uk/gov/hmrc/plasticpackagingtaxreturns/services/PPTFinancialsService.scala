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

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  FinancialDataResponse,
  FinancialTransaction
}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTFinancialsService.Charge

import java.time.LocalDate

class PPTFinancialsService {

  def construct(data: FinancialDataResponse): PPTFinancials = {
    val charges: Seq[Charge] =
      data.financialTransactions.collect { case ft if ft.outstandingAmount.forall(_ != 0) => Charge(ft) }

    val dueCharge: Option[Charge] =
      charges.filter(_.due.isEqualOrAfterToday).sortBy(_.due).headOption

    val overdueSum: BigDecimal =
      charges.filter(_.due.isBeforeToday).map(_.amount).sum

    val overdueAmount: Option[BigDecimal] = Some(overdueSum).filter(_ > 0)

    val dueSum: Option[BigDecimal]                   = dueCharge.map(due => if (overdueSum < 0) due.amount + overdueSum else due.amount)
    val debitAmount: Option[(BigDecimal, LocalDate)] = dueSum.flatMap(sum => dueCharge.map(sum -> _.due))

    val totalChargesSum = charges.map(_.amount).sum

    if (totalChargesSum < 0) PPTFinancials.inCredit(totalChargesSum)
    else
      (debitAmount, overdueAmount) match {
        case (None, None)                         => PPTFinancials.NothingOutstanding
        case (Some((amount, due)), None)          => PPTFinancials.debitDue(amount, due)
        case (None, Some(amount))                 => PPTFinancials.overdue(amount)
        case (Some((amount, due)), Some(overdue)) => PPTFinancials.debitAndOverdue(amount, due, overdue)
      }
  }

}

object PPTFinancialsService {
  private final case class Charge(amount: BigDecimal, due: LocalDate)

  private object Charge {

    def apply(financialTransaction: FinancialTransaction): Charge =
      (financialTransaction.outstandingAmount, financialTransaction.items.headOption.flatMap(_.dueDate)) match {
        case (Some(amount), Some(due)) => new Charge(amount, due)
        case _                         => throw new Exception("Failed to extract charge from financialTransaction")
      }

  }

}
