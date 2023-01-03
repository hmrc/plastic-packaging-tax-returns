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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDetail

final case class PPTObligations(
  nextObligation: Option[ObligationDetail],
  oldestOverdueObligation: Option[ObligationDetail],
  overdueObligationCount: Int,
  isNextObligationDue: Boolean,
  displaySubmitReturnsLink: Boolean
)

object PPTObligations {
  implicit val PPTObligationsWrites: OWrites[PPTObligations] = Json.writes[PPTObligations]
}
