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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.CustomerType.CustomerType

case class CustomerDetails(
  customerType: CustomerType,
  individualDetails: Option[IndividualDetails] = None,
  organisationDetails: Option[OrganisationDetails] = None
)

object CustomerDetails {
  implicit val format: OFormat[CustomerDetails] = Json.format[CustomerDetails]

}
