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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.helpers

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{FinancialDataResponse, FinancialItem, FinancialTransaction}

import java.time.{LocalDate, LocalDateTime}

object FinancialTransactionHelper {
  def createResponseWithDdInProgressFlag(periodKey: String) = {

    val items = Seq(
      createDdFinancialItem(),
      createDdFinancialItem(DDcollectionInProgress = Some(true)),
      createDdFinancialItem(DDcollectionInProgress = Some(false))
    )
    FinancialDataResponse(
      idType = None,
      idNumber = None,
      regimeType = None,
      processingDate = LocalDateTime.now(),
      financialTransactions = Seq(createFinancialTransaction(periodKey = periodKey, items = items))
    )
  }
  def createFinancialResponseWithAmount(periodKey: String = "period-key", amount: BigDecimal = BigDecimal(0.0)) = {
    FinancialDataResponse(
      idType = None,
      idNumber = None,
      regimeType = None,
      processingDate = LocalDateTime.now(),
      financialTransactions =
        Seq(
          createFinancialTransaction(
            periodKey = periodKey,
            amount = amount,
            items = Seq(createDdFinancialItem(amount)))
        )
    )
  }

  private def createFinancialTransaction
  (
    amount: BigDecimal = BigDecimal(0.0),
    periodKey: String = "period-key",
    items: Seq[FinancialItem]) = {
    FinancialTransaction(
      chargeType = None,
      mainType = None,
      periodKey = Some(periodKey),
      periodKeyDescription = None,
      taxPeriodFrom = None,
      taxPeriodTo = None,
      outstandingAmount = Some(amount),
      items = items
    )
  }

  private def createDdFinancialItem(amount: BigDecimal = BigDecimal(0.0), DDcollectionInProgress: Option[Boolean] = None) = {
    FinancialItem(
      subItem = None,
      dueDate = Some(LocalDate.now()),
      amount = Some(amount),
      clearingDate = None,
      clearingReason = None,
      DDcollectionInProgress = DDcollectionInProgress
    )
  }

}
