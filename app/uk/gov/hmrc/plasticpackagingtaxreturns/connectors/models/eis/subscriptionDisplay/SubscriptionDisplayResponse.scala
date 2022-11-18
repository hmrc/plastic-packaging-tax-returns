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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipSubscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails.Update
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest

case class SubscriptionDisplayResponse(
  processingDate: String,
  changeOfCircumstanceDetails: Option[ChangeOfCircumstanceDetails],
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: BigDecimal,
  declaration: Declaration,
  groupPartnershipSubscription: Option[GroupPartnershipSubscription]
){

  def toUpdateRequest: SubscriptionUpdateRequest =
    SubscriptionUpdateRequest(
      changeOfCircumstanceDetails = ChangeOfCircumstanceDetails(Update, None),
      legalEntityDetails = legalEntityDetails,
      principalPlaceOfBusinessDetails = principalPlaceOfBusinessDetails,
      primaryContactDetails = primaryContactDetails,
      businessCorrespondenceDetails = businessCorrespondenceDetails,
      taxObligationStartDate = taxObligationStartDate,
      last12MonthTotalTonnageAmt = last12MonthTotalTonnageAmt,
      declaration = declaration,
      groupPartnershipSubscription = groupPartnershipSubscription
    )
}

object SubscriptionDisplayResponse {

  implicit val format: OFormat[SubscriptionDisplayResponse] =
    Json.format[SubscriptionDisplayResponse]

}
