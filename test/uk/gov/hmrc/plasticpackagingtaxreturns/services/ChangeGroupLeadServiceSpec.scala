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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsNull
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.{GroupPartnershipDetails, GroupPartnershipSubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails.Update
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.changeGroupLead._

import java.time.LocalDate

class ChangeGroupLeadServiceSpec extends PlaySpec {

  val sut = new ChangeGroupLeadService()

  "change" must {

    "attempt to remove the current Rep from the members list" when {
      "it is there" in {
        val sub = createSubscription(
          defaultMember,
          createMember("Hooks Pirates Ltd").copy(relationship = "Representative")
        )

        val result = sut.changeSubscription(sub, defaultUserAnswers)

        result.groupPartnershipSubscription.get.groupPartnershipDetails.find(_.relationship == "Representative") mustBe None
        result.groupPartnershipSubscription.get.groupPartnershipDetails.find(_.organisationDetails.get.organisationName == "Lost Boys Ltd-organisationName") mustBe None
      }

      "it is not there" in {
        val sub = createSubscription(defaultMember)

        val result = sut.changeSubscription(sub, defaultUserAnswers)

        result.groupPartnershipSubscription.get.groupPartnershipDetails.find(_.relationship == "Representative") mustBe None
        result.groupPartnershipSubscription.get.groupPartnershipDetails.find(_.organisationDetails.get.organisationName == "Lost Boys Ltd-organisationName") mustBe None
      }
    }

    "put the old representative member in to the members list as just a standard member" in {
      val sub = createSubscription(defaultMember)

      val result = sut.changeSubscription(sub, defaultUserAnswers)

      result.groupPartnershipSubscription.get.groupPartnershipDetails.map(_.organisationDetails.get.organisationName) mustBe List("original-rep-organisationName")
    }

    "remove the New Representative member from the normal members list" in {
      val sub = createSubscription(defaultMember)

      val result = sut.changeSubscription(sub, defaultUserAnswers)

      result.groupPartnershipSubscription.get.groupPartnershipDetails.find(_.organisationDetails.get.organisationName == "Lost Boys Ltd-organisationName") mustBe None
    }

    "fill all the details of the representative member with the selected member details and user answers" in {
      val sub = createSubscription(defaultMember)

      val result = sut.changeSubscription(sub, defaultUserAnswers)

      result.legalEntityDetails.customerDetails.organisationDetails.get.organisationName mustBe "Lost Boys Ltd-organisationName"
      result.legalEntityDetails.customerDetails.organisationDetails.get.organisationType mustBe Some("Lost Boys Ltd-organisationType")
      result.legalEntityDetails.customerIdentification1 mustBe "Lost Boys Ltd-customerIdentification1"
      result.legalEntityDetails.customerIdentification2 mustBe Some("Lost Boys Ltd-customerIdentification2")

      result.principalPlaceOfBusinessDetails.addressDetails.addressLine1 mustBe "Lost Boys Ltd-addressLine1"
      result.principalPlaceOfBusinessDetails.addressDetails.addressLine2 mustBe "Lost Boys Ltd-addressLine2"
      result.principalPlaceOfBusinessDetails.addressDetails.countryCode mustBe "Lost Boys Ltd-countryCode"

      result.primaryContactDetails.name mustBe "Peter Pan"
      result.primaryContactDetails.positionInCompany mustBe "Lost Boy"
      result.primaryContactDetails.contactDetails.email mustBe "Lost Boys Ltd-email"
      result.primaryContactDetails.contactDetails.telephone mustBe "Lost Boys Ltd-telephone"

      result.businessCorrespondenceDetails.addressLine1 mustBe "2nd Star to the right"
      result.businessCorrespondenceDetails.addressLine2 mustBe "Straight on til morning"
      result.businessCorrespondenceDetails.countryCode mustBe "NL"
    }

    "convert the subscription in to an update subscription request" in {
      val sub = createSubscription(defaultMember)

      val result = sut.changeSubscription(sub, defaultUserAnswers)

      result.changeOfCircumstanceDetails.changeOfCircumstance mustBe Update
      result.changeOfCircumstanceDetails.deregistrationDetails mustBe None
    }

    "error" when {

      Seq(
        ChooseNewGroupLeadGettable,
        MainContactNameGettable,
        MainContactJobTitleGettable,
        NewGroupLeadEnterContactAddressGettable
      ).foreach { gettable =>
        s"user answers does not contain ${gettable.getClass.getSimpleName}" in {
          val userAnswers = defaultUserAnswers
            .setUnsafe(gettable.path, JsNull) //unset the specific default

          val sub = createSubscription(defaultMember)

          val ex = intercept[IllegalStateException](sut.changeSubscription(sub, userAnswers))
          ex.getMessage mustBe s"${gettable.path} is missing from useranswers"
        }
      }

      "the subscription is not a group" in {
        val sub = createSubscription().copy(groupPartnershipSubscription = None)

        val ex = intercept[IllegalStateException](sut.changeSubscription(sub, defaultUserAnswers))
        ex.getMessage mustBe "Change group lead not a group"
      }

      "Selected New Representative member is not part of the group" in {
        val sub = createSubscription(Seq.empty: _*)

        val ex = intercept[IllegalStateException](sut.changeSubscription(sub, defaultUserAnswers))
        ex.getMessage mustBe "Selected New Representative member is not part of the group"
      }

      "member of group missing organisation" in {
        val brokenMember = createMember("broken").copy(organisationDetails = None)

        val sub = createSubscription(defaultMember, brokenMember)

        val ex = intercept[IllegalStateException](sut.changeSubscription(sub, defaultUserAnswers))
        ex.getMessage mustBe "member of group missing organisation"

      }
    }
  }

  def defaultUserAnswers: UserAnswers =
    UserAnswers("")
      .setUnsafe(ChooseNewGroupLeadGettable, "Lost Boys Ltd-organisationName")
      .setUnsafe(MainContactNameGettable, "Peter Pan")
      .setUnsafe(MainContactJobTitleGettable, "Lost Boy")
      .setUnsafe(NewGroupLeadEnterContactAddressGettable,
        BusinessCorrespondenceDetails("2nd Star to the right", "Straight on til morning", None, None, None, "NL")
      )


  def createSubscription(members: GroupPartnershipDetails*): SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(
      legalEntityDetails = LegalEntityDetails(
        dateOfApplication = LocalDate.now.toString,
        customerIdentification1 = "original-rep-customerIdentification1",
        customerIdentification2 = Some("original-rep-customerIdentification2"),
        customerDetails =
          CustomerDetails(
            CustomerType.Organisation,
            organisationDetails = Some(
              OrganisationDetails(
                organisationType = Some("original-rep-organisationType"),
                organisationName = "original-rep-organisationName"
              )
            )
          ),
        groupSubscriptionFlag = true
      ),
      principalPlaceOfBusinessDetails =
        PrincipalPlaceOfBusinessDetails(
          addressDetails = AddressDetails(
            addressLine1 = "original-rep-principal-addressLine1",
            addressLine2 = "original-rep-principal-addressLine2",
            countryCode = "original-rep-principal-countryCode"
          ), contactDetails = ContactDetails(
            email = "original-rep-email",
            telephone = "original-rep-telephone"
          )
        ),
      primaryContactDetails = PrimaryContactDetails(
        name = "original-rep-primary-contact-name",
        contactDetails = ContactDetails(
          email = "original-rep-primary-contact-email",
          telephone = "original-rep-primary-contact-telephone"
        ),
        positionInCompany = "original-rep-primary-contact-positionInCompany"
      ),
      businessCorrespondenceDetails =
        BusinessCorrespondenceDetails(
          addressLine1 = "original-rep-contact-addressLine1",
          addressLine2 = "original-rep-contact-addressLine2",
          countryCode = "original-rep-contact-countryCode"
        ),
      declaration = Declaration(true),
      taxObligationStartDate = LocalDate.now.toString,
      last12MonthTotalTonnageAmt = 123,
      groupPartnershipSubscription = Some(
        GroupPartnershipSubscription(
          representativeControl = true,
          allMembersControl = true,
          groupPartnershipDetails = members.toList
        )
      ),
      processingDate = LocalDate.now.toString,
      changeOfCircumstanceDetails = None
    )
  def defaultMember: GroupPartnershipDetails = createMember("Lost Boys Ltd")

  def createMember(member: String, relationship: String = "Member"): GroupPartnershipDetails =
    GroupPartnershipDetails(
      relationship = relationship,
      customerIdentification1 = s"$member-customerIdentification1",
      customerIdentification2 = Some(s"$member-customerIdentification2"),
      organisationDetails = Some(OrganisationDetails(
        organisationType = Some(s"$member-organisationType"),
        organisationName = s"$member-organisationName"
      )),
      individualDetails = None,
      addressDetails = AddressDetails(
        addressLine1 = s"$member-addressLine1",
        addressLine2 = s"$member-addressLine2",
        countryCode = s"$member-countryCode"
      ),
      contactDetails = ContactDetails(
        email = s"$member-email",
        telephone = s"$member-telephone"
      ),
      regWithoutIDFlag = false
    )

}
