package uk.gov.hmrc.plasticpackagingtaxreturns.services

import play.api.libs.json.JsPath
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.{CustomerDetails, CustomerType, IndividualDetails, OrganisationDetails, PrimaryContactDetails, PrincipalPlaceOfBusinessDetails, Subscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipDetails
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.{ChangeOfCircumstanceDetails, SubscriptionDisplayResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers

class ChangeGroupLeadService {

  def changeSubscription(subscription: SubscriptionDisplayResponse, userAnswers: UserAnswers) = {

    val oldRepresentativeAsStandardMember = createMemberFromPreviousRepresentative(subscription)

    val members = subscription
      .groupPartnershipSubscription
      .getOrElse(throw new IllegalStateException("Change group lead not a group"))
      .groupPartnershipDetails
      .filterNot(_.relationship == "Representative") // data cleanse as registration has added Representative member to the member list

    val newRepName = userAnswers.get[String](JsPath \ "changeGroupLead" \ "chooseNewGroupLead")
      .getOrElse(throw new IllegalStateException("no new representative selected"))

    val newRepMemberDetails = members.find(_.organisationDetails.getOrElse(throw new IllegalStateException("member of group missing organisation")).organisationName == newRepName)
        .getOrElse(throw new IllegalStateException("Selected New Representative member is not part of the group"))

    val otherMembers = members.filterNot(
        _.organisationDetails.getOrElse(throw new IllegalStateException("member of group missing organisation"))
          .organisationName == newRepName
        )

    val newMembersList = oldRepresentativeAsStandardMember :: otherMembers

    subscription.copy(
        legalEntityDetails = subscription.legalEntityDetails.copy(
        customerDetails = CustomerDetails(
          customerType = CustomerType.Organisation,
          individualDetails = None,
          organisationDetails = newRepMemberDetails.organisationDetails,
        )
      ),
      principalPlaceOfBusinessDetails = PrincipalPlaceOfBusinessDetails(
        addressDetails = newRepMemberDetails.addressDetails,
        contactDetails = newRepMemberDetails.contactDetails
      ),
      primaryContactDetails = PrimaryContactDetails(
        name = ???, //todo from useranswers
        contactDetails = newRepMemberDetails.contactDetails,
        positionInCompany = ???, //todo from useranswers
      ),
      businessCorrespondenceDetails = ???, //todo from useranswers
      groupPartnershipSubscription = subscription.groupPartnershipSubscription.map(
        _.copy(
          groupPartnershipDetails = newMembersList
        ))
    )
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
