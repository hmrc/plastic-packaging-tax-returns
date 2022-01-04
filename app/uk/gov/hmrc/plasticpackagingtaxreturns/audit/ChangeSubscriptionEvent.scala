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

package uk.gov.hmrc.plasticpackagingtaxreturns.audit

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipSubscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.NrsDetails

import java.time.ZonedDateTime

case class ChangeSubscriptionEvent(
  changeOfCircumstanceDetails: Option[ChangeOfCircumstanceDetails],
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: BigDecimal,
  declaration: Declaration,
  groupSubscription: Option[GroupPartnershipSubscription],
  pptReference: Option[String],
  processingDateTime: Option[ZonedDateTime],
  nrsDetails: Option[NrsDetails]
)

object ChangeSubscriptionEvent {

  implicit val format: OFormat[ChangeSubscriptionEvent] = Json.format[ChangeSubscriptionEvent]
  val eventType: String                                 = "changePPTRegistration"

  def apply(
    subscription: Subscription,
    pptReference: Option[String],
    processingDateTime: Option[ZonedDateTime]
  ): ChangeSubscriptionEvent =
    ChangeSubscriptionEvent(changeOfCircumstanceDetails = subscription.changeOfCircumstanceDetails,
                            legalEntityDetails = subscription.legalEntityDetails,
                            principalPlaceOfBusinessDetails = subscription.principalPlaceOfBusinessDetails,
                            primaryContactDetails = subscription.primaryContactDetails,
                            businessCorrespondenceDetails = subscription.businessCorrespondenceDetails,
                            taxObligationStartDate = subscription.taxObligationStartDate,
                            last12MonthTotalTonnageAmt =
                              BigDecimal(subscription.last12MonthTotalTonnageAmt),
                            declaration = subscription.declaration,
                            groupSubscription = subscription.groupPartnershipSubscription,
                            pptReference = pptReference,
                            processingDateTime = processingDateTime,
                            nrsDetails = subscription.nrsDetails
    )

}
