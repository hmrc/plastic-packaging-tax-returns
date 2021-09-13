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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipSubscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails

case class SubscriptionUpdateRequest(
  changeOfCircumstanceDetails: ChangeOfCircumstanceDetails,
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: Option[BigDecimal],
  declaration: Declaration,
  groupSubscription: Option[GroupPartnershipSubscription],
  userHeaders: Option[Map[String, String]] = None
) {

  def toSubscription: Subscription =
    Subscription(changeOfCircumstanceDetails = Some(this.changeOfCircumstanceDetails),
                 legalEntityDetails = this.legalEntityDetails,
                 principalPlaceOfBusinessDetails = this.principalPlaceOfBusinessDetails,
                 primaryContactDetails = this.primaryContactDetails,
                 businessCorrespondenceDetails = this.businessCorrespondenceDetails,
                 declaration = this.declaration,
                 taxObligationStartDate = this.taxObligationStartDate,
                 last12MonthTotalTonnageAmt = Some(this.last12MonthTotalTonnageAmt.getOrElse(BigDecimal(0)).toLong),
                 groupPartnershipSubscription = this.groupSubscription
    )

}

object SubscriptionUpdateRequest {
  implicit val format: OFormat[SubscriptionUpdateRequest] = Json.format[SubscriptionUpdateRequest]

}
