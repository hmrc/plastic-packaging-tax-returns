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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models

import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.{
  ChangeOfCircumstanceDetails,
  SubscriptionDisplayResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.models.registration._

import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime.now
import java.time.format.DateTimeFormatter
import java.util.UUID

trait SubscriptionTestData {

  protected def ukLimitedCompanyPptSubscription(pptReference: String = UUID.randomUUID().toString): PptSubscription =
    PptSubscription(pptReference = pptReference,
                    primaryContactDetails =
                      uk.gov.hmrc.plasticpackagingtaxreturns.models.registration.PrimaryContactDetails(
                        Some("FirstName LastName"),
                        jobTitle = Some("CEO"),
                        email =
                          Some("test@test.com"),
                        phoneNumber =
                          Some("1234567890"),
                        address = Some(
                          Address(addressLine1 =
                                    "addressLine1",
                                  addressLine2 =
                                    "line2",
                                  addressLine3 =
                                    Some("Town"),
                                  postCode =
                                    Some("PostCode")
                          )
                        )
                      ),
                    organisationDetails =
                      uk.gov.hmrc.plasticpackagingtaxreturns.models.registration.OrganisationDetails(isBasedInUk =
                                                                                                       Some(true),
                                                                                                     organisationType =
                                                                                                       Some(
                                                                                                         "UK_COMPANY"
                                                                                                       ),
                                                                                                     businessRegisteredAddress =
                                                                                                       Some(
                                                                                                         Address(
                                                                                                           addressLine1 =
                                                                                                             "addressLine1",
                                                                                                           addressLine3 =
                                                                                                             Some(
                                                                                                               "Town"
                                                                                                             ),
                                                                                                           addressLine2 =
                                                                                                             "line2",
                                                                                                           postCode =
                                                                                                             Some(
                                                                                                               "PostCode"
                                                                                                             )
                                                                                                         )
                                                                                                       ),
                                                                                                     safeNumber =
                                                                                                       Some("123"),
                                                                                                     incorporationDetails =
                                                                                                       Some(
                                                                                                         IncorporationDetails(
                                                                                                           companyName =
                                                                                                             Some(
                                                                                                               "Plastics Limited"
                                                                                                             ),
                                                                                                           phoneNumber =
                                                                                                             Some(
                                                                                                               "077665544"
                                                                                                             ),
                                                                                                           email = Some(
                                                                                                             "email@gmail.com"
                                                                                                           )
                                                                                                         )
                                                                                                       )
                      )
    )

  def soleTraderPptSubscription(pptReference: String = UUID.randomUUID().toString): PptSubscription = {
    val regDetails = ukLimitedCompanyPptSubscription(pptReference)
    regDetails.copy(organisationDetails =
      regDetails.organisationDetails.copy(
        soleTraderDetails =
          Some(SoleTraderIncorporationDetails(firstName = Some("James"), lastName = Some("Bond"))),
        incorporationDetails = None
      )
    )
  }

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
    last12MonthTotalTonnageAmt = Some(15000),
    declaration = Declaration(declarationBox1 = true),
    groupSubscription = None
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
                                  ChangeOfCircumstanceDetails(changeOfCircumstance =
                                    "update"
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
                                  subscription.last12MonthTotalTonnageAmt.map(_.toLong),
                                declaration =
                                  subscription.declaration,
                                groupSubscription =
                                  subscription.groupSubscription
    )

  protected def createSubscriptionUpdateResponse(
    subscription: Subscription,
    changeOfCircumstanceDetails: ChangeOfCircumstanceDetails
  ) =
    SubscriptionUpdateRequest(changeOfCircumstanceDetails = changeOfCircumstanceDetails,
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
                                subscription.last12MonthTotalTonnageAmt.map(_.toLong),
                              declaration =
                                subscription.declaration,
                              groupSubscription =
                                subscription.groupSubscription
    )

}
