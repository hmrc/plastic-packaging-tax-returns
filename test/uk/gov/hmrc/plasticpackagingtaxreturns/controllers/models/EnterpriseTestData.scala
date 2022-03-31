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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models

import java.time.{LocalDate, LocalDateTime}

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  FinancialDataResponse,
  FinancialItem,
  FinancialTransaction
}

trait EnterpriseTestData {

  val financialDataResponse: FinancialDataResponse = FinancialDataResponse(idType = Some("idType"),
                                                                           idNumber = Some("idNumber"),
                                                                           regimeType = Some("regimeType"),
                                                                           processingDate = LocalDateTime.now(),
                                                                           financialTransactions = Seq(
                                                                             FinancialTransaction(
                                                                               chargeType = Some("chargeType"),
                                                                               mainType = Some("mainType"),
                                                                               periodKey = Some("periodKey"),
                                                                               periodKeyDescription =
                                                                                 Some("periodKeyDescription"),
                                                                               taxPeriodFrom = Some(LocalDate.now()),
                                                                               taxPeriodTo = Some(LocalDate.now()),
                                                                               outstandingAmount =
                                                                                 Some(BigDecimal(1000)),
                                                                               items = Seq(
                                                                                 FinancialItem(
                                                                                   subItem = Some("subItem"),
                                                                                   dueDate =
                                                                                     Some(LocalDate.now()),
                                                                                   amount =
                                                                                     Some(BigDecimal(1000)),
                                                                                   clearingDate =
                                                                                     Some(LocalDate.now()),
                                                                                   clearingReason =
                                                                                     Some("clearingReason")
                                                                                 )
                                                                               )
                                                                             )
                                                                           )
  )

}
