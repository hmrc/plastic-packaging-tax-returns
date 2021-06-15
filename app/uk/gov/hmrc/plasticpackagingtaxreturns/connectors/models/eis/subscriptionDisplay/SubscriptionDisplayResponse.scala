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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.group.GroupSubscription
import uk.gov.hmrc.plasticpackagingtaxreturns.models.registration.{
  Address,
  IncorporationDetails,
  PptSubscription,
  SoleTraderIncorporationDetails,
  OrganisationDetails => PPTOrganisationDetails,
  PrimaryContactDetails => PPTPrimaryContactDetails
}

case class SubscriptionDisplayResponse(
  processingDate: String,
  changeOfCircumstanceDetails: ChangeOfCircumstanceDetails,
  legalEntityDetails: LegalEntityDetails,
  principalPlaceOfBusinessDetails: PrincipalPlaceOfBusinessDetails,
  primaryContactDetails: PrimaryContactDetails,
  businessCorrespondenceDetails: BusinessCorrespondenceDetails,
  taxObligationStartDate: String,
  last12MonthTotalTonnageAmt: Option[BigDecimal],
  declaration: Declaration,
  groupSubscription: Option[GroupSubscription]
) {

  def toPptSubscription(pptReference: String): PptSubscription =
    PptSubscription(pptReference = pptReference,
                    primaryContactDetails = toPptPrimaryContactDetails,
                    organisationDetails = toPptOrganisationDetails
    )

  private def toPptOrganisationDetails = {
    val pptOrgDetails = PPTOrganisationDetails(
      organisationType =
        this.legalEntityDetails.customerDetails.organisationDetails.flatMap(_.organisationType),
      businessRegisteredAddress = Some(toPptAddress(this.principalPlaceOfBusinessDetails.addressDetails))
    )
    populateOrganisationTypeDetails(pptOrgDetails)
  }

  private def populateOrganisationTypeDetails(organisationDetails: PPTOrganisationDetails): PPTOrganisationDetails =
    this.legalEntityDetails.customerDetails.customerType match {
      case CustomerType.Individual   => organisationDetails.copy(soleTraderDetails = getSoleTraderDetails)
      case CustomerType.Organisation => organisationDetails.copy(incorporationDetails = getUkCompanyDetails)
    }

  private def getUkCompanyDetails =
    Some(
      IncorporationDetails(
        companyName =
          this.legalEntityDetails.customerDetails.organisationDetails.flatMap(_.organisationName),
        phoneNumber = Some(this.principalPlaceOfBusinessDetails.contactDetails.telephone),
        email = Some(this.principalPlaceOfBusinessDetails.contactDetails.email)
      )
    )

  private def getSoleTraderDetails =
    Some(
      SoleTraderIncorporationDetails(
        firstName = this.legalEntityDetails.customerDetails.individualDetails.map(_.firstName),
        lastName = this.legalEntityDetails.customerDetails.individualDetails.map(_.lastName)
      )
    )

  private def toPptAddress(addressDetails: AddressDetails) =
    Address(addressLine1 = addressDetails.addressLine1,
            addressLine2 = addressDetails.addressLine2,
            addressLine3 = addressDetails.addressLine3,
            addressLine4 = addressDetails.addressLine4,
            postCode = addressDetails.postalCode
    )

  private def toPptAddress(businessCorrespondenceDetails: BusinessCorrespondenceDetails) =
    Address(addressLine1 = businessCorrespondenceDetails.addressLine1,
            addressLine2 = businessCorrespondenceDetails.addressLine2,
            addressLine3 = businessCorrespondenceDetails.addressLine3,
            addressLine4 = businessCorrespondenceDetails.addressLine4,
            postCode = businessCorrespondenceDetails.postalCode
    )

  private def toPptPrimaryContactDetails =
    PPTPrimaryContactDetails(fullName = Some(this.primaryContactDetails.name),
                             address = Some(toPptAddress(this.businessCorrespondenceDetails)),
                             jobTitle = Some(this.primaryContactDetails.positionInCompany),
                             email = Some(this.primaryContactDetails.contactDetails.email),
                             phoneNumber = Some(this.primaryContactDetails.contactDetails.telephone)
    )

}

object SubscriptionDisplayResponse {

  implicit val format: OFormat[SubscriptionDisplayResponse] =
    Json.format[SubscriptionDisplayResponse]

}
