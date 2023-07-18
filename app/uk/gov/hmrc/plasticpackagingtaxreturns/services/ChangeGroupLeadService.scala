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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipDetails.Relationship
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.nonRepudiation.NrsSubscriptionUpdateSubmission
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.changeGroupLead._

class ChangeGroupLeadService {

  def createNrsSubscriptionUpdateSubmission(subscriptionUpdateRequest: SubscriptionUpdateRequest, 
    userAnswers: UserAnswers): NrsSubscriptionUpdateSubmission = {
    
    NrsSubscriptionUpdateSubmission(userAnswers, subscriptionUpdateRequest)
  }
  
  def createSubscriptionUpdateRequest(subscription: SubscriptionDisplayResponse, userAnswers: UserAnswers): SubscriptionUpdateRequest = {

    val oldRepresentativeAsStandardMember: GroupPartnershipDetails = createMemberFromPreviousRepresentative(subscription)

    val members = subscription
      .groupPartnershipSubscription
      .getOrElse(throw new IllegalStateException("Change group lead not a group"))
      .groupPartnershipDetails
      .filterNot(_.relationship == Relationship.Representative)

    val newRepOrganisation = userAnswers.getOrFail(ChooseNewGroupLeadGettable)
    val newRepContactName = userAnswers.getOrFail(MainContactNameGettable)
    val newRepContactJobTitle = userAnswers.getOrFail(MainContactJobTitleGettable)
    val newRepContactAddress = userAnswers.getOrFail(NewGroupLeadEnterContactAddressGettable)

    val newRepOriginalMemberDetails = members
      .find(member => member.organisationDetails.exists(_.organisationName == newRepOrganisation.organisationName)
        && member.customerIdentification1 == newRepOrganisation.crn)
      .getOrElse(throw new IllegalStateException("Selected New Representative member is not part of the group"))

    val otherMembers = members.map{ member =>
      val memberDetails = member.organisationDetails.getOrElse(throw new IllegalStateException("member of group missing organisation"))
      if(memberDetails.organisationName == newRepOrganisation.organisationName && member.customerIdentification1 == newRepOrganisation.crn)
        member.copy(relationship = Relationship.Representative)
      else
        member
    }

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
        regWithoutIDFlag = newRepOriginalMemberDetails.regWithoutIDFlag
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
      relationship = Relationship.Member,
      customerIdentification1 = subscription.legalEntityDetails.customerIdentification1,
      customerIdentification2 = subscription.legalEntityDetails.customerIdentification2,
      organisationDetails = subscription.legalEntityDetails.customerDetails.organisationDetails,
      individualDetails = Some(IndividualDetails(
        firstAndLastNameSplit(subscription.primaryContactDetails.name)
      )),
      addressDetails = subscription.principalPlaceOfBusinessDetails.addressDetails,
      contactDetails = subscription.principalPlaceOfBusinessDetails.contactDetails,
      regWithoutIDFlag = subscription.legalEntityDetails.regWithoutIDFlag
    )
  }

  private def firstAndLastNameSplit(name: String): (String, String) = name.trim.split(" ").toList match {
    case first :: remaining =>
      (first, remaining.mkString(" "))
    case _ => ("", "")
  }
}
