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

import org.joda.time.LocalDate
import play.api.libs.json.JodaWrites.DefaultJodaLocalDateWrites
import play.api.libs.json.{Json, OWrites, Writes}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDetail

final case class Obligation(fromDate: LocalDate, toDate: LocalDate, dueDate: LocalDate, periodKey: String)

object Obligation {
  implicit val DateWrites: Writes[LocalDate] = DefaultJodaLocalDateWrites
  implicit val ObligationWrites: Writes[Obligation] = Json.writes[Obligation]
}

final case class PPTObligations(nextObligation: Option[Obligation])

object PPTObligations {
  implicit val PPTObligationsWrites: OWrites[PPTObligations] = Json.writes[PPTObligations]
}
