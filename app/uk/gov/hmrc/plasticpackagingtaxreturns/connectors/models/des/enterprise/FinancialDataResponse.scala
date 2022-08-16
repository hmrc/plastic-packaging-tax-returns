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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise

import java.time.{LocalDate, LocalDateTime}

import play.api.libs.json.{Json, OFormat}

case class FinancialDataResponse(
  idType: Option[String],
  idNumber: Option[String],
  regimeType: Option[String],
  processingDate: LocalDateTime,
  financialTransactions: Seq[FinancialTransaction]
)

object FinancialDataResponse {

  implicit val format: OFormat[FinancialDataResponse] = Json.format[FinancialDataResponse]
  
  def inferNoTransactions(pptReference: String, processingDate: LocalDateTime): FinancialDataResponse = 
    FinancialDataResponse(
      idType = Some("ZPPT"), 
      idNumber = Some(pptReference), 
      regimeType = Some("PPT"), 
      processingDate = processingDate, 
      financialTransactions = Seq()
    ) 

}

case class FinancialTransaction(
  chargeType: Option[String],
  mainType: Option[String],
  periodKey: Option[String],
  periodKeyDescription: Option[String],
  taxPeriodFrom: Option[LocalDate],
  taxPeriodTo: Option[LocalDate],
  outstandingAmount: Option[BigDecimal],
  items: Seq[FinancialItem]
)

object FinancialTransaction {

  implicit val format: OFormat[FinancialTransaction] =
    Json.format[FinancialTransaction]

}

case class FinancialItem(
  subItem: Option[String],
  dueDate: Option[LocalDate],
  amount: Option[BigDecimal],
  clearingDate: Option[LocalDate],
  clearingReason: Option[String],
  DDcollectionInProgress: Option[Boolean] // TODO check this in real payload
)

object FinancialItem {

  implicit val format: OFormat[FinancialItem] =
    Json.format[FinancialItem]

}
