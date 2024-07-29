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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json
import play.api.libs.json._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupPartnershipDetails.Relationship
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.{GroupPartnershipDetails, GroupPartnershipSubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.ChangeOfCircumstanceDetails.Update
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.{ChangeOfCircumstanceDetails, SubscriptionDisplayResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.changeGroupLead._
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers

import java.time.LocalDate

class ChangeGroupLeadServiceSpec extends PlaySpec {

  val sut = new ChangeGroupLeadService()

  "createSubscriptionUpdateRequest" must {

    "convert the new representative member in the members list to have a relationship of 'Representative'" in {
      val sub = createSubscription(defaultMember)

      val result = sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers)

      val member = result.groupPartnershipSubscription.get.groupPartnershipDetails.find(
        _.organisationDetails.get.organisationName == "Lost Boys Ltd-organisationName"
      ).get
      member.relationship mustBe "Representative"
    }

    "put the original representative member in to the members list as just a standard member" in {
      val sub = createSubscription(defaultMember)

      val result = sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers)

      result.groupPartnershipSubscription.get.groupPartnershipDetails.map(_.organisationDetails.get.organisationName) must contain(
        "original-rep-organisationName"
      )
      val member = result.groupPartnershipSubscription.get.groupPartnershipDetails.find(
        _.organisationDetails.get.organisationName == "original-rep-organisationName"
      ).get
      member.relationship mustBe "Member"
    }

    "return only one representative member in the members list" in {
      val sub = createSubscription(defaultMember, createMember("Tinkerbell"))

      val result = sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers)

      result.groupPartnershipSubscription.get.groupPartnershipDetails.count(_.relationship == Relationship.Representative) mustBe 1
      withClue("members list should contain all members including representative") {
        result.groupPartnershipSubscription.get.groupPartnershipDetails.size mustBe 3
      }
    }

    "fill all the details of the representative member with the selected member details and user answers" in {
      val sub = createSubscription(defaultMember)

      val result = sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers)

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

      val result = sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers)

      result.changeOfCircumstanceDetails.changeOfCircumstance mustBe Update
      result.changeOfCircumstanceDetails.deregistrationDetails mustBe None
    }

    "error" when {

      Seq(ChooseNewGroupLeadGettable, MainContactNameGettable, MainContactJobTitleGettable, NewGroupLeadEnterContactAddressGettable).foreach {
        gettable =>
          s"user answers does not contain ${gettable.getClass.getSimpleName}" in {
            val userAnswers = defaultUserAnswers
              .setUnsafe(gettable.path, JsNull) //unset the specific default

            val sub = createSubscription(defaultMember)

            an[IllegalStateException] must be thrownBy sut.createSubscriptionUpdateRequest(sub, userAnswers)
          }
      }

      "the subscription is not a group" in {
        val sub = createSubscription().copy(groupPartnershipSubscription = None)

        val ex = intercept[IllegalStateException](sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers))
        ex.getMessage mustBe "Change group lead not a group"
      }

      "Selected New Representative member is not part of the group" in {
        val sub = createSubscription(Seq.empty: _*)

        val ex = intercept[IllegalStateException](sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers))
        ex.getMessage mustBe "Selected New Representative member is not part of the group"
      }

      "Selected New Representative member's name doesn't match anyone in members list" in {
        val sub = createSubscription(defaultMember)

        val userAnswers = defaultUserAnswers.setUnsafe(ChooseNewGroupLeadGettable, Member("unmatchable", "Lost Boys Ltd-customerIdentification1"))

        val ex = intercept[IllegalStateException](sut.createSubscriptionUpdateRequest(sub, userAnswers))
        ex.getMessage mustBe "Selected New Representative member is not part of the group"
      }

      "Selected New Representative member's crn doesn't match anyone in members list" in {
        val sub = createSubscription(defaultMember)

        val userAnswers = defaultUserAnswers.setUnsafe(ChooseNewGroupLeadGettable, Member("Lost Boys Ltd-organisationName", "unmatchable"))

        val ex = intercept[IllegalStateException](sut.createSubscriptionUpdateRequest(sub, userAnswers))
        ex.getMessage mustBe "Selected New Representative member is not part of the group"
      }

      "member of group missing organisation" in {
        val brokenMember = createMember("broken").copy(organisationDetails = None)

        val sub = createSubscription(defaultMember, brokenMember)

        val ex = intercept[IllegalStateException](sut.createSubscriptionUpdateRequest(sub, defaultUserAnswers))
        ex.getMessage mustBe "member of group missing organisation"

      }
    }

  }

  "createNrsSubscriptionUpdateSubmission" must {

    "include UserAnswers" in {
      val userAnswers: UserAnswers        = UserAnswers("id").setUnsafe(json.__ \ "biscuits", "chocolate")
      val nrsSubscriptionUpdateSubmission = sut.createNrsSubscriptionUpdateSubmission(cookOneUp, userAnswers)
      val asJson: JsValue                 = Json.toJson(nrsSubscriptionUpdateSubmission)
      (asJson \ "userAnswers") mustBe JsDefined(Json.parse("""{"biscuits":"chocolate"}"""))
    }

    "include subscription update payload" in {
      val nrsSubscriptionUpdateSubmission = sut.createNrsSubscriptionUpdateSubmission(cookOneUp, UserAnswers("id"))
      val asJson: JsValue                 = Json.toJson(nrsSubscriptionUpdateSubmission)
      val maybeJsObject                   = (asJson \ "subscriptionUpdateRequest").asOpt[JsObject]
      maybeJsObject must not be None

      val jsObject = maybeJsObject.get
      jsObject.value.keys must contain("changeOfCircumstanceDetails")
      jsObject.value.keys must contain("legalEntityDetails")
      jsObject.value.keys must contain("groupPartnershipSubscription")
      jsObject.value.keys must contain("businessCorrespondenceDetails")
    }

  }

  def defaultUserAnswers: UserAnswers =
    UserAnswers("user-answers-id")
      .setUnsafe(ChooseNewGroupLeadGettable, Member("Lost Boys Ltd-organisationName", "Lost Boys Ltd-customerIdentification1"))
      .setUnsafe(MainContactNameGettable, "Peter Pan")
      .setUnsafe(MainContactJobTitleGettable, "Lost Boy")
      .setUnsafe(
        NewGroupLeadEnterContactAddressGettable,
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
            organisationDetails =
              Some(OrganisationDetails(organisationType = Some("original-rep-organisationType"), organisationName = "original-rep-organisationName"))
          ),
        groupSubscriptionFlag = true,
        regWithoutIDFlag = false
      ),
      principalPlaceOfBusinessDetails =
        PrincipalPlaceOfBusinessDetails(
          addressDetails = AddressDetails(
            addressLine1 = "original-rep-principal-addressLine1",
            addressLine2 = "original-rep-principal-addressLine2",
            countryCode = "original-rep-principal-countryCode"
          ),
          contactDetails = Some(ContactDetails(email = "original-rep-email", telephone = "original-rep-telephone"))
        ),
      primaryContactDetails = PrimaryContactDetails(
        name = "original-rep-primary-contact-name",
        contactDetails = ContactDetails(email = "original-rep-primary-contact-email", telephone = "original-rep-primary-contact-telephone"),
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
          representativeControl = Some(true),
          allMembersControl = Some(true),
          groupPartnershipDetails = originalRepMember :: members.toList
        )
      ),
      processingDate = LocalDate.now.toString,
      changeOfCircumstanceDetails = None
    )

  def originalRepMember                      = createMember("original-rep", "Representative")
  def defaultMember: GroupPartnershipDetails = createMember("Lost Boys Ltd")

  def createMember(member: String, relationship: String = Relationship.Member): GroupPartnershipDetails =
    GroupPartnershipDetails(
      relationship = relationship,
      customerIdentification1 = s"$member-customerIdentification1",
      customerIdentification2 = Some(s"$member-customerIdentification2"),
      organisationDetails =
        Some(OrganisationDetails(organisationType = Some(s"$member-organisationType"), organisationName = s"$member-organisationName")),
      individualDetails = None,
      addressDetails =
        AddressDetails(addressLine1 = s"$member-addressLine1", addressLine2 = s"$member-addressLine2", countryCode = s"$member-countryCode"),
      contactDetails = ContactDetails(email = s"$member-email", telephone = s"$member-telephone"),
      regWithoutIDFlag = false
    )

  private def cookOneUp: SubscriptionUpdateRequest =
    new SubscriptionUpdateRequest(
      changeOfCircumstanceDetails = ChangeOfCircumstanceDetails(
        changeOfCircumstance = "",   // some enum from docs - eg DEREGISTER but not that!
        deregistrationDetails = None // as we're not de-reg here
      ),
      legalEntityDetails = LegalEntityDetails(
        dateOfApplication = "",                     // stay as is
        customerIdentification1 = "",               // copy from member details
        customerIdentification2 = Some(""),         // copy from member details
        customerDetails = CustomerDetails(          // <-- largely copied from member details below
          customerType = CustomerType.Organisation, // Always this value?
          individualDetails = None,                 // Always none as always organisation type?
          organisationDetails = Some(
            OrganisationDetails(
              organisationType = Some(""), // copy from member details
              organisationName = ""        // copy from member details
            )
          )
        ),
        groupSubscriptionFlag = true,       // stay as is - would be funny if false
        regWithoutIDFlag = false,           // stay as is
        partnershipSubscriptionFlag = false // stay as is
      ),
      principalPlaceOfBusinessDetails = PrincipalPlaceOfBusinessDetails(
        addressDetails = someAddressDetails,      // copy from member details
        contactDetails = Some(someContactDetails) // copy from member details
      ),
      primaryContactDetails = PrimaryContactDetails(
        name = "",                           // from form
        contactDetails = someContactDetails, // copied from member details? Do we need a 2nd set of email, phone and mobile?
        positionInCompany = ""               // from form, job title
      ),
      businessCorrespondenceDetails = someAddressDetails2, // from form for contact address
      taxObligationStartDate = "",                         // stay as is
      last12MonthTotalTonnageAmt = BigDecimal(0),          // stay as is
      declaration = Declaration(true),                     // stay as is
      groupPartnershipSubscription = Some(
        GroupPartnershipSubscription(
          representativeControl = Some(false), // stay as is
          allMembersControl = Some(false),     // stay as is
          groupPartnershipDetails = List(
            GroupPartnershipDetails(              // <------------- list of members starts here ------>
              relationship = "",                  // "Member"
              customerIdentification1 = "",       // for previous rep, copy from legalEntityDetails
              customerIdentification2 = Some(""), // for previous rep, copy from legalEntityDetails
              organisationDetails = Some(
                OrganisationDetails( // for previous rep, copy from legalEntityDetails
                  organisationType = Some(""),
                  organisationName = ""
                )
              ),
              individualDetails = Some(IndividualDetails(title = Some(""), firstName = "", middleName = Some(""), lastName = "")),
              addressDetails = someAddressDetails, // for previous rep, copy from principalPlaceOfBusinessDetails.contactDetails
              contactDetails = someContactDetails, // for previous rep, copy from principalPlaceOfBusinessDetails.contactDetails
              regWithoutIDFlag = false
            )
          )
        )
      )
    )

  private def someAddressDetails2 =
    BusinessCorrespondenceDetails(
      addressLine1 = "",
      addressLine2 = "",
      addressLine3 = Some(""), // these should be shunted up
      addressLine4 = Some(""), // ^  ^  ^
      postalCode = Some(""),
      countryCode = ""
    )

  private def someAddressDetails =
    AddressDetails(
      addressLine1 = "",
      addressLine2 = "",
      addressLine3 = Some(""), // these should be shunted up
      addressLine4 = Some(""), // ^  ^  ^
      postalCode = Some(""),
      countryCode = ""
    )

  private def someContactDetails = ContactDetails(email = "", telephone = "", mobileNumber = Some(""))
}
