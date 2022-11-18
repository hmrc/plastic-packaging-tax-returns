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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.{CustomerDetails, CustomerType, IndividualDetails, PrimaryContactDetails, PrincipalPlaceOfBusinessDetails}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails.Update
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.{ChangeOfCircumstanceDetails, SubscriptionDisplayResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.changeGroupLead._

class ChangeGroupLeadService {

  def changeSubscription(subscription: SubscriptionDisplayResponse, userAnswers: UserAnswers): SubscriptionUpdateRequest = {

    val oldRepresentativeAsStandardMember = createMemberFromPreviousRepresentative(subscription)

    val members = subscription
      .groupPartnershipSubscription
      .getOrElse(throw new IllegalStateException("Change group lead not a group"))
      .groupPartnershipDetails
      .filterNot(_.relationship == "Representative") // data cleanse as registration has added Representative member to the member list

    val newRepOrganisationName = userAnswers.getOrFail(ChooseNewGroupLeadGettable)
    val newRepContactName = userAnswers.getOrFail(MainContactNameGettable)
    val newRepContactJobTitle = userAnswers.getOrFail(MainContactJobTitleGettable)
    val newRepContactAddress = userAnswers.getOrFail(NewGroupLeadEnterContactAddressGettable)

    val newRepOriginalMemberDetails = members
      .find(_.organisationDetails.exists(_.organisationName == newRepOrganisationName))
      .getOrElse(throw new IllegalStateException("Selected New Representative member is not part of the group"))

    val otherMembers = members.filterNot(
        _.organisationDetails.getOrElse(throw new IllegalStateException("member of group missing organisation"))
          .organisationName == newRepOrganisationName
        )

    val newMembersList = oldRepresentativeAsStandardMember :: otherMembers

    subscription.copy(
        legalEntityDetails = subscription.legalEntityDetails.copy(
        customerDetails = CustomerDetails(
          customerType = CustomerType.Organisation,
          individualDetails = None,
          organisationDetails = newRepOriginalMemberDetails.organisationDetails,
        ),
          customerIdentification1 = newRepOriginalMemberDetails.customerIdentification1,
          customerIdentification2 = newRepOriginalMemberDetails.customerIdentification2,
          regWithoutIDFlag = Some(newRepOriginalMemberDetails.regWithoutIDFlag)
      ),
      principalPlaceOfBusinessDetails = PrincipalPlaceOfBusinessDetails(
        addressDetails = newRepOriginalMemberDetails.addressDetails,
        contactDetails = newRepOriginalMemberDetails.contactDetails
      ),
      primaryContactDetails = PrimaryContactDetails(
        name = newRepContactName,
        contactDetails = newRepOriginalMemberDetails.contactDetails,
        positionInCompany = newRepContactJobTitle,
      ),
      businessCorrespondenceDetails = newRepContactAddress,
      groupPartnershipSubscription = subscription.groupPartnershipSubscription.map(
        _.copy(
          groupPartnershipDetails = newMembersList
        ))
    ).toUpdateRequest
  }

  private def createMemberFromPreviousRepresentative(subscription: SubscriptionDisplayResponse): GroupPartnershipDetails = {
    GroupPartnershipDetails(
      relationship = "Member",
      customerIdentification1 = subscription.legalEntityDetails.customerIdentification1,
      customerIdentification2 = subscription.legalEntityDetails.customerIdentification2,
      organisationDetails = subscription.legalEntityDetails.customerDetails.organisationDetails,
      individualDetails = Some(IndividualDetails(
        firstAndLastNameSplit(subscription.primaryContactDetails.name)
      )),
      addressDetails = subscription.principalPlaceOfBusinessDetails.addressDetails,
      contactDetails = subscription.principalPlaceOfBusinessDetails.contactDetails,
      regWithoutIDFlag = subscription.legalEntityDetails.regWithoutIDFlag.getOrElse(false)
    )
  }

  private def firstAndLastNameSplit(name: String): (String, String) =
    if (name.trim.nonEmpty) {
      val first :: remaining = name.trim.split(" ").toList
      (first, remaining.mkString(" "))
    } else
      ("", "")

}
