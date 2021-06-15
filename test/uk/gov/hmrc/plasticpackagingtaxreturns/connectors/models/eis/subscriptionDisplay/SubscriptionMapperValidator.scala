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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.models.registration.PptSubscription

trait SubscriptionMapperValidator extends AnyWordSpec with Matchers {

  def validatePptSubscriptionMapping(
    pptReference: String,
    pptSubscription: PptSubscription,
    subscription: Subscription
  ): Unit = {

    pptSubscription.pptReference mustBe pptReference

    pptSubscription.primaryContactDetails.fullName mustBe Some(subscription.primaryContactDetails.name)
    pptSubscription.primaryContactDetails.jobTitle mustBe Some(subscription.primaryContactDetails.positionInCompany)
    pptSubscription.primaryContactDetails.email mustBe Some(subscription.primaryContactDetails.contactDetails.email)
    pptSubscription.primaryContactDetails.phoneNumber mustBe Some(
      subscription.primaryContactDetails.contactDetails.telephone
    )
    pptSubscription.primaryContactDetails.address.get.addressLine1 mustBe
      subscription.businessCorrespondenceDetails.addressLine1

    pptSubscription.primaryContactDetails.address.get.addressLine2 mustBe
      subscription.businessCorrespondenceDetails.addressLine2

    pptSubscription.primaryContactDetails.address.get.addressLine3 mustBe
      subscription.businessCorrespondenceDetails.addressLine3

    pptSubscription.primaryContactDetails.address.get.addressLine4 mustBe
      subscription.businessCorrespondenceDetails.addressLine4

    pptSubscription.primaryContactDetails.address.get.postCode mustBe
      subscription.businessCorrespondenceDetails.postalCode

    pptSubscription.organisationDetails.organisationType mustBe Some(
      subscription.legalEntityDetails.customerDetails.organisationDetails.get.organisationType.get
    )
    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine1 mustBe
      subscription.principalPlaceOfBusinessDetails.addressDetails.addressLine1

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine2 mustBe
      subscription.principalPlaceOfBusinessDetails.addressDetails.addressLine2

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine3 mustBe
      subscription.principalPlaceOfBusinessDetails.addressDetails.addressLine3

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine4 mustBe
      subscription.principalPlaceOfBusinessDetails.addressDetails.addressLine4

    pptSubscription.organisationDetails.businessRegisteredAddress.get.postCode mustBe
      subscription.principalPlaceOfBusinessDetails.addressDetails.postalCode
  }

  protected def validateSoleTaderPptSubscriptionMapping(
    pptReference: String,
    pptSubscription: PptSubscription,
    soleTraderSubscription: Subscription
  ): Unit = {
    pptSubscription.pptReference mustBe pptReference

    pptSubscription.primaryContactDetails.fullName mustBe Some(soleTraderSubscription.primaryContactDetails.name)
    pptSubscription.primaryContactDetails.jobTitle mustBe Some(
      soleTraderSubscription.primaryContactDetails.positionInCompany
    )
    pptSubscription.primaryContactDetails.email mustBe Some(
      soleTraderSubscription.primaryContactDetails.contactDetails.email
    )
    pptSubscription.primaryContactDetails.phoneNumber mustBe Some(
      soleTraderSubscription.primaryContactDetails.contactDetails.telephone
    )
    pptSubscription.primaryContactDetails.address.get.addressLine1 mustBe
      soleTraderSubscription.businessCorrespondenceDetails.addressLine1

    pptSubscription.primaryContactDetails.address.get.addressLine2 mustBe
      soleTraderSubscription.businessCorrespondenceDetails.addressLine2

    pptSubscription.primaryContactDetails.address.get.addressLine3 mustBe
      soleTraderSubscription.businessCorrespondenceDetails.addressLine3

    pptSubscription.primaryContactDetails.address.get.addressLine4 mustBe
      soleTraderSubscription.businessCorrespondenceDetails.addressLine4

    pptSubscription.primaryContactDetails.address.get.postCode mustBe
      soleTraderSubscription.businessCorrespondenceDetails.postalCode

    pptSubscription.organisationDetails.organisationType mustBe None

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine1 mustBe
      soleTraderSubscription.principalPlaceOfBusinessDetails.addressDetails.addressLine1

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine2 mustBe
      soleTraderSubscription.principalPlaceOfBusinessDetails.addressDetails.addressLine2

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine3 mustBe
      soleTraderSubscription.principalPlaceOfBusinessDetails.addressDetails.addressLine3

    pptSubscription.organisationDetails.businessRegisteredAddress.get.addressLine4 mustBe
      soleTraderSubscription.principalPlaceOfBusinessDetails.addressDetails.addressLine4

    pptSubscription.organisationDetails.businessRegisteredAddress.get.postCode mustBe
      soleTraderSubscription.principalPlaceOfBusinessDetails.addressDetails.postalCode
  }

}
