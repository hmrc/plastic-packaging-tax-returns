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

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials

import java.time.{LocalDate, LocalDateTime}
import scala.util.Random

class PPTFinancialsServiceSpec extends PlaySpec {

  val sut: PPTFinancialsService = new PPTFinancialsService()
  val today: LocalDate          = LocalDate.now()
  val yesterday: LocalDate      = today.minusDays(1)
  val lastWeek: LocalDate       = today.minusDays(7)
  val tomorrow: LocalDate       = today.plusDays(1)

  val amount  = BigDecimal(Math.abs(Random.nextInt()))
  val amount2 = BigDecimal(Math.abs(Random.nextInt()))
  val amount3 = BigDecimal(Math.abs(Random.nextInt()))

  val emptyData        = FinancialDataResponse(None, None, None, LocalDateTime.now(), Nil)
  val emptyTransaction = FinancialTransaction(None, None, None, None, None, None, None, Nil)
  val emptyItem        = FinancialItem(None, None, None, None, None)

  def makeData(charges: (BigDecimal, LocalDate)*): FinancialDataResponse =
    emptyData.copy(financialTransactions =
      charges.map {
        case (amount, due) =>
          emptyTransaction.copy(outstandingAmount = Some(amount), items = Seq(emptyItem.copy(dueDate = Some(due))))
      }
    )

  "construct" must {
    "return NothingDue" when {
      "there is no FinancialTransactions" in {
        sut.construct(makeData()) mustBe PPTFinancials.NothingOutstanding
      }

      "there is no outstanding amount" in {
        sut.construct(makeData(BigDecimal(0) -> today)) mustBe PPTFinancials.NothingOutstanding
      }
    }
    "return debitDue" when {
      "there is only a debit due" in {
        sut.construct(makeData(amount -> today)) mustBe PPTFinancials.debitDue(amount, today)
      }
      "there is credit on the due debit" in {
        sut.construct(makeData(BigDecimal(-10) -> yesterday, amount -> today)) mustBe PPTFinancials.debitDue(
          amount - 10,
          today
        )
      }
    }
    "return overdue" when {
      "there is an overdue FinancialTransaction" in {
        sut.construct(makeData(amount -> yesterday)) mustBe PPTFinancials.overdue(amount)
      }
      "there is multiple overdue FinancialTransaction" in {
        sut.construct(makeData(amount -> yesterday, amount2 -> lastWeek)) mustBe PPTFinancials.overdue(amount + amount2)
      }
    }
    "return debitAndOverdue" when {
      val due            = amount  -> today
      val overdue        = amount2 -> yesterday
      val anotherOverdue = amount3 -> lastWeek
      "there is a due and overdue FinancialTransaction" in {
        sut.construct(makeData(due, overdue)) mustBe PPTFinancials.debitAndOverdue(amount, today, amount2)
      }
      "there is a due and multiple overdue FinancialTransaction" in {
        sut.construct(makeData(due, overdue, anotherOverdue)) mustBe PPTFinancials.debitAndOverdue(amount,
                                                                                                   today,
                                                                                                   amount2 + amount3
        )
      }
    }
    "return in credit" when {
      val credit = -amount
      "due in credit" in {
        sut.construct(makeData(credit -> today)) mustBe PPTFinancials.inCredit(amount)
      }
      "overdue in credit" in {
        sut.construct(makeData(credit -> yesterday)) mustBe PPTFinancials.inCredit(amount)
      }
      "overdue in credit more than due is due" in {
        val fullCredit = BigDecimal.valueOf(Double.MinValue)
        sut.construct(makeData(fullCredit -> yesterday, amount -> today)) mustBe PPTFinancials.inCredit(
          fullCredit + amount
        )
      }
    }

    "throw exception" when {
      "FinancialTransactions is missing amount outstanding" in {
        intercept[Exception](
          sut.construct(
            emptyData.copy(financialTransactions =
              Seq(emptyTransaction.copy(outstandingAmount = None, items = Seq(emptyItem.copy(dueDate = Some(today)))))
            )
          )
        ).getMessage mustBe "Failed to extract charge from financialTransaction"
      }

      "FinancialTransactions is missing due date" in {
        intercept[Exception](
          sut.construct(
            emptyData.copy(financialTransactions =
              Seq(emptyTransaction.copy(outstandingAmount = Some(amount), items = Seq(emptyItem.copy(dueDate = None))))
            )
          )
        ).getMessage mustBe "Failed to extract charge from financialTransaction"
      }

      "FinancialTransactions is missing a FinancialItem" in {
        intercept[Exception](
          sut.construct(
            emptyData.copy(financialTransactions = Seq(emptyTransaction.copy(outstandingAmount = Some(amount))))
          )
        ).getMessage mustBe "Failed to extract charge from financialTransaction"
      }
    }
  }
}
