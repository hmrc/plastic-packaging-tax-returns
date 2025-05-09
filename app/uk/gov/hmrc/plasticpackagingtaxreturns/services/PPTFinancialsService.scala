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

import play.api.Logging
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  FinancialDataResponse,
  FinancialTransaction
}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTFinancialsService.Charge
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.time.LocalDate
import javax.inject.Inject

class PPTFinancialsService @Inject() (implicit edgeOfSystem: EdgeOfSystem) extends Logging {

  def lookUpForDdInProgress(periodKey: String, response: FinancialDataResponse): Boolean =
    response.financialTransactions.find(o => o.periodKey == Some(periodKey))
      .fold(false)(_.items.find(_.DDcollectionInProgress == Some(true)).isDefined)

  def construct(data: FinancialDataResponse): PPTFinancials = {
    val charges: Seq[Charge] =
      data.financialTransactions.collect { case ft if ft.outstandingAmount.forall(_ != 0) => Charge(ft) }

    val totalChargesSum = charges.map(_.amount).sum

    if (totalChargesSum == 0) PPTFinancials.NothingOutstanding
    else if (totalChargesSum < 0) PPTFinancials.inCredit(totalChargesSum)
    else {
      val dueDateOpt: Option[LocalDate] =
        charges.filter(_.due.isEqualOrAfterToday).sortBy(_.due).headOption.map(_.due)

      val overdueSum: BigDecimal =
        charges.filter(_.due.isBeforeToday).map(_.amount).sum

      if (overdueSum == totalChargesSum) PPTFinancials.overdue(totalChargesSum)
      else {
        val dueDate = dueDateOpt.getOrElse(throw new Exception("Due date missing"))
        if (overdueSum <= 0) PPTFinancials.debitDue(totalChargesSum, dueDate)
        else PPTFinancials.debitAndOverdue(totalChargesSum, dueDate, overdueSum)
      }
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
