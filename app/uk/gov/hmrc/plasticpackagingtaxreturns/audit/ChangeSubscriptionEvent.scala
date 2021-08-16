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

package uk.gov.hmrc.plasticpackagingtaxreturns.audit

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupSubscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.{
  BusinessCorrespondenceDetails,
  Declaration,
  LegalEntityDetails,
  PrimaryContactDetails,
  PrincipalPlaceOfBusinessDetails
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest

case class ChangeSubscriptionEvent(
  changeOfCircumstanceDetails: ChangeOfCircumstanceDetails,
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: Option[BigDecimal],
  declaration: Declaration,
  groupSubscription: Option[GroupSubscription],
  pptReference: Option[String]
)

object ChangeSubscriptionEvent {

  implicit val format: OFormat[ChangeSubscriptionEvent] = Json.format[ChangeSubscriptionEvent]
  val eventType: String                                 = "CHANGE_PPT_REGISTRATION"

  def apply(
    subscriptionUpdateRequest: SubscriptionUpdateRequest,
    pptReference: Option[String]
  ): ChangeSubscriptionEvent =
    ChangeSubscriptionEvent(changeOfCircumstanceDetails = subscriptionUpdateRequest.changeOfCircumstanceDetails,
                            legalEntityDetails = subscriptionUpdateRequest.legalEntityDetails,
                            principalPlaceOfBusinessDetails = subscriptionUpdateRequest.principalPlaceOfBusinessDetails,
                            primaryContactDetails = subscriptionUpdateRequest.primaryContactDetails,
                            businessCorrespondenceDetails = subscriptionUpdateRequest.businessCorrespondenceDetails,
                            taxObligationStartDate = subscriptionUpdateRequest.taxObligationStartDate,
                            last12MonthTotalTonnageAmt = subscriptionUpdateRequest.last12MonthTotalTonnageAmt,
                            declaration = subscriptionUpdateRequest.declaration,
                            groupSubscription = subscriptionUpdateRequest.groupSubscription,
                            pptReference = pptReference
    )

}
