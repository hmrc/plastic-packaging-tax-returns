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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import play.api.libs.json.{Json, OWrites}

import java.time.LocalDate

final case class Charge(amount: BigDecimal, date: LocalDate)

object Charge {
  implicit val writes: OWrites[Charge] = Json.writes[Charge]
}

final case class PPTFinancials(creditAmount: Option[BigDecimal], debitAmount: Option[Charge], overdueAmount: Option[BigDecimal])

object PPTFinancials {

  val NothingOutstanding: PPTFinancials = new PPTFinancials(None, None, None)

  def debitDue(amount: BigDecimal, dueDate: LocalDate): PPTFinancials =
    new PPTFinancials(None, debitAmount = Some(Charge(amount, dueDate)), None)

  def overdue(amount: BigDecimal): PPTFinancials =
    new PPTFinancials(None, None, overdueAmount = Some(amount))

  def inCredit(amount: BigDecimal): PPTFinancials =
    new PPTFinancials(creditAmount = Some(amount.abs), None, None)

  def debitAndOverdue(dueAmount: BigDecimal, dueDate: LocalDate, overdueAmount: BigDecimal) =
    new PPTFinancials(None, debitAmount = Some(Charge(dueAmount, dueDate)), overdueAmount = Some(overdueAmount))

  implicit val PPTFinancialsWrites: OWrites[PPTFinancials] = Json.writes[PPTFinancials]
}
