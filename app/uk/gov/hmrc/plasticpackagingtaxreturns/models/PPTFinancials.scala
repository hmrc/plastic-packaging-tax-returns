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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import play.api.libs.json.{Json, OWrites}

import java.time.LocalDate

final case class PPTFinancials(
  creditAmount: Option[BigDecimal],
  debitAmount: Option[(BigDecimal, LocalDate)],
  overdueAmount: Option[BigDecimal]
)

object PPTFinancials {

  val NothingDue: PPTFinancials                   = PPTFinancials(None, None, None)
  def inCredit(amount: BigDecimal): PPTFinancials = NothingDue.copy(creditAmount = Some(amount))

  def debitDue(amount: BigDecimal, dueDate: LocalDate): PPTFinancials =
    NothingDue.copy(debitAmount = Some((amount, dueDate)))

  def overdue(amount: BigDecimal): PPTFinancials = NothingDue.copy(overdueAmount = Some(amount))

  implicit val PPTFinancialsWrites: OWrites[PPTFinancials] = Json.writes[PPTFinancials]
}
