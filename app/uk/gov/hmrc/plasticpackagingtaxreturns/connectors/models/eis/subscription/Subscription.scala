/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupSubscription

import scala.language.implicitConversions

case class Subscription(
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  declaration: Declaration,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: Option[Long] = None,
  groupSubscription: Option[GroupSubscription] = None
)

object Subscription {
  implicit val format: OFormat[Subscription] = Json.format[Subscription]

}