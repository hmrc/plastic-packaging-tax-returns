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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{ChargeDetails, EisReturnDetails, IdDetails, Return}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.{GroupPartnershipDetails, GroupPartnershipSubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.{ChangeOfCircumstanceDetails, SubscriptionDisplayResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest

import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime.now
import java.time.format.DateTimeFormatter

trait SubscriptionTestData {

  protected val pptUserHeaders: Map[String, String] = Map("testHeaderKey" -> "testHeaderValue")

  private val pptReference = "XMPPT0000000123"

  protected val ukLimitedCompanySubscription: Subscription = Subscription(
    legalEntityDetails =
      LegalEntityDetails(dateOfApplication =
                           now(UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                         customerIdentification1 = "123456789",
                         customerIdentification2 = Some("1234567890"),
                         customerDetails = CustomerDetails(customerType = CustomerType.Organisation,
                                                           organisationDetails =
                                                             Some(
                                                               subscription.OrganisationDetails(
                                                                 organisationName = Some("Plastics Ltd"),
                                                                 organisationType = Some("UK Limited Company")
                                                               )
                                                             )
                         )
      ),
    principalPlaceOfBusinessDetails =
      PrincipalPlaceOfBusinessDetails(
        addressDetails = AddressDetails(addressLine1 = "2-3 Scala Street",
                                        addressLine2 = "London",
                                        postalCode = Some("W1T 2HN"),
                                        countryCode = "GB"
        ),
        contactDetails = ContactDetails(email = "test@test.com", telephone = "02034567890")
      ),
    primaryContactDetails =
      uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.PrimaryContactDetails(
        name = "Kevin Durant",
        contactDetails =
          ContactDetails(email = "test@test.com", telephone = "02034567890"),
        positionInCompany = "Director"
      ),
    businessCorrespondenceDetails = BusinessCorrespondenceDetails(addressLine1 = "2-3 Scala Street",
                                                                  addressLine2 = "London",
                                                                  postalCode = Some("W1T 2HN"),
                                                                  countryCode = "GB"
    ),
    taxObligationStartDate = now(UTC).toString,
    last12MonthTotalTonnageAmt = 15000,
    declaration = Declaration(declarationBox1 = true),
    groupPartnershipSubscription = None
  )

  protected val ukLimitedCompanyGroupMember = GroupPartnershipDetails(relationship = "Representative",
                                                                      customerIdentification1 = "abc123",
                                                                      customerIdentification2 = Some("def456"),
                                                                      organisationDetails = Some(
                                                                        OrganisationDetails(Some("UK Limited Company"),
                                                                                            Some("Subsidiary 1")
                                                                        )
                                                                      ),
                                                                      individualDetails = None,
                                                                      addressDetails =
                                                                        AddressDetails(addressLine1 =
                                                                                         "2-3 Scala Street",
                                                                                       addressLine2 = "Soho",
                                                                                       countryCode = "GB"
                                                                        ),
                                                                      contactDetails =
                                                                        ContactDetails(email = "man@firm.com",
                                                                                       telephone = "01274 873264"
                                                                        ),
                                                                      regWithoutIDFlag = false
  )

  protected val soleTraderGroupMember = GroupPartnershipDetails(relationship = "Representative",
                                                                customerIdentification1 = "abc123",
                                                                customerIdentification2 = Some("def456"),
                                                                organisationDetails = None,
                                                                individualDetails = Some(
                                                                  IndividualDetails(firstName = "Andrew",
                                                                                    lastName = "Man"
                                                                  )
                                                                ),
                                                                addressDetails = AddressDetails(addressLine1 =
                                                                                                  "2-3 Scala Street",
                                                                                                addressLine2 = "Soho",
                                                                                                countryCode = "GB"
                                                                ),
                                                                contactDetails = ContactDetails(email = "aman@firm.com",
                                                                                                telephone =
                                                                                                  "01274 873264"
                                                                ),
                                                                regWithoutIDFlag = false
  )

  protected val ukLimitedCompanyGroupSubscription: Subscription =
    ukLimitedCompanySubscription.copy(groupPartnershipSubscription =
      Some(
        GroupPartnershipSubscription(representativeControl = true,
                                     allMembersControl = true,
                                     groupPartnershipDetails = List(ukLimitedCompanyGroupMember, soleTraderGroupMember)
        )
      )
    )

  protected val soleTraderSubscription: Subscription = {
    val subscription = ukLimitedCompanySubscription.copy(legalEntityDetails =
      LegalEntityDetails(dateOfApplication =
                           now(UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                         customerIdentification1 = "123456789",
                         customerIdentification2 = Some("1234567890"),
                         customerDetails =
                           CustomerDetails(
                             customerType = CustomerType.Individual,
                             individualDetails =
                               Some(IndividualDetails(title = Some("MR"), firstName = "James", lastName = "Bond"))
                           )
      )
    )
    subscription
  }

  protected def createSubscriptionDisplayResponse(subscription: Subscription) =
    SubscriptionDisplayResponse(processingDate = "2020-05-05",
                                changeOfCircumstanceDetails =
                                  Some(
                                    ChangeOfCircumstanceDetails(changeOfCircumstance =
                                      "update"
                                    )
                                  ),
                                legalEntityDetails =
                                  subscription.legalEntityDetails,
                                principalPlaceOfBusinessDetails =
                                  subscription.principalPlaceOfBusinessDetails,
                                primaryContactDetails =
                                  subscription.primaryContactDetails,
                                businessCorrespondenceDetails =
                                  subscription.businessCorrespondenceDetails,
                                taxObligationStartDate =
                                  subscription.taxObligationStartDate,
                                last12MonthTotalTonnageAmt =
                                  subscription.last12MonthTotalTonnageAmt.longValue(),
                                declaration =
                                  subscription.declaration,
                                groupOrPartnershipSubscription =
                                  subscription.groupPartnershipSubscription
    )

  protected def createSubscriptionUpdateRequest(subscription: Subscription): SubscriptionUpdateRequest =
    SubscriptionUpdateRequest(
      changeOfCircumstanceDetails =
        subscription.changeOfCircumstanceDetails.getOrElse(ChangeOfCircumstanceDetails("02")),
      legalEntityDetails =
        subscription.legalEntityDetails,
      principalPlaceOfBusinessDetails =
        subscription.principalPlaceOfBusinessDetails,
      primaryContactDetails =
        subscription.primaryContactDetails,
      businessCorrespondenceDetails =
        subscription.businessCorrespondenceDetails,
      taxObligationStartDate =
        subscription.taxObligationStartDate,
      last12MonthTotalTonnageAmt =
        subscription.last12MonthTotalTonnageAmt.longValue(),
      declaration =
        subscription.declaration,
      groupPartnershipSubscription =
        subscription.groupPartnershipSubscription,
      userHeaders = Some(pptUserHeaders)
    )

}
