/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.{JsResultException, Json}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateSuccessfulResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

class SubscriptionsConnectorSpec
    extends ConnectorISpec with Injector with SubscriptionTestData with ScalaFutures with EitherValues {

  private lazy val connector: SubscriptionsConnector = app.injector.instanceOf[SubscriptionsConnector]

  private val displaySubscriptionTimer = "ppt.subscription.display.timer"
  private val updateSubscriptionTimer  = "ppt.subscription.update.timer"

  private val pptReference          = UUID.randomUUID().toString
  private val subscriptionUpdateUrl = s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    wiremock.resetAll()
  }

  "Subscription connector" when {
    "requesting a subscription" should {
      "handle a 200 for uk company subscription" in {

        stubSubscriptionDisplay(pptReference, createSubscriptionDisplayResponse(ukLimitedCompanySubscription))

        await(connector.getSubscription(pptReference))

        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }

      "handle 200 for sole trader subscription" in {

        stubSubscriptionDisplay(pptReference, createSubscriptionDisplayResponse(soleTraderSubscription))

        await(connector.getSubscription(pptReference))

        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }

      "handle 200 for body with Illegal unquoted character ((CTRL-CHAR, code 9))" in {

        val illegalData = "test data HQ UC	  " // due to the \t(tabb) on the end not escaped
        val body =
          s"""{
             |  "processingDate" : "2020-05-05",
             |  "changeOfCircumstanceDetails" : {
             |    "changeOfCircumstance" : "update"
             |  },
             |  "legalEntityDetails" : {
             |    "dateOfApplication" : "2022-07-25",
             |    "customerIdentification1" : "123456789",
             |    "customerIdentification2" : "1234567890",
             |    "customerDetails" : {
             |      "customerType" : "Organisation",
             |      "organisationDetails" : {
             |        "organisationType" : "UK Limited Company",
             |        "organisationName" : "$illegalData"
             |      }
             |    },
             |    "groupSubscriptionFlag" : false,
             |    "regWithoutIDFlag" : false,
             |    "partnershipSubscriptionFlag" : false
             |  },
             |  "principalPlaceOfBusinessDetails" : {
             |    "addressDetails" : {
             |      "addressLine1" : "2-3 Scala Street",
             |      "addressLine2" : "London",
             |      "postalCode" : "W1T 2HN",
             |      "countryCode" : "GB"
             |    },
             |    "contactDetails" : {
             |      "email" : "test@test.com",
             |      "telephone" : "02034567890"
             |    }
             |  },
             |  "primaryContactDetails" : {
             |    "name" : "Kevin Durant",
             |    "contactDetails" : {
             |      "email" : "test@test.com",
             |      "telephone" : "02034567890"
             |    },
             |    "positionInCompany" : "Director"
             |  },
             |  "businessCorrespondenceDetails" : {
             |    "addressLine1" : "2-3 Scala Street",
             |    "addressLine2" : "London",
             |    "postalCode" : "W1T 2HN",
             |    "countryCode" : "GB"
             |  },
             |  "taxObligationStartDate" : "2022-07-25T14:49:18.363Z",
             |  "last12MonthTotalTonnageAmt" : 15000,
             |  "declaration" : {
             |    "declarationBox1" : true
             |  }
             |}""".stripMargin

        stubFor(
          get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(body)
            )
        )

        await(connector.getSubscription(pptReference))

        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }

      "return details of the group members" in {
        stubFor(get(anyUrl()).willReturn(ok.withBody(subscriptionResponseBodyWithGroupMembers)))

        val response = await(connector.getSubscription("ppt-ref"))

        response.value.groupPartnershipSubscription.isDefined mustBe true
        response.value.groupPartnershipSubscription.get.groupPartnershipDetails must have length 3
        val iterator = response.value.groupPartnershipSubscription.get.groupPartnershipDetails.iterator
        iterator.next().organisationDetails.get.organisationName mustBe "Test Company Ltd UK"
      }

      "handle unexpected failure responses" in {

        stubFor(
          get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        intercept[JsResultException] {
          await(connector.getSubscription(pptReference))
        }
      }
    }

    "requesting update subscription" should {
      "handle a 200 for updating uk company subscription" in {

        val subscriptionProcessingDate = ZonedDateTime.now(ZoneOffset.UTC).toString
        val formBundleNumber           = "1234567890"

        stubSubscriptionUpdate(pptReference, subscriptionProcessingDate, formBundleNumber)

        val updateDetails =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
        val res: SubscriptionUpdateSuccessfulResponse =
          await(connector.updateSubscription(pptReference, updateDetails))

        res.pptReferenceNumber mustBe pptReference
        res.formBundleNumber mustBe formBundleNumber
        res.processingDate mustBe ZonedDateTime.parse(subscriptionProcessingDate)
        getTimer(updateSubscriptionTimer).getCount mustBe 1
      }

      "handle 200 for sole trader subscription" in {

        val subscriptionProcessingDate = ZonedDateTime.now(ZoneOffset.UTC).toString
        val formBundleNumber           = "1234567890"

        stubSubscriptionUpdate(pptReference, subscriptionProcessingDate, formBundleNumber)

        val updateDetails =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription)

        val res: SubscriptionUpdateSuccessfulResponse =
          await(connector.updateSubscription(pptReference, updateDetails))

        res.pptReferenceNumber mustBe pptReference
        res.formBundleNumber mustBe formBundleNumber
        res.processingDate mustBe ZonedDateTime.parse(subscriptionProcessingDate)
        getTimer(updateSubscriptionTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {

        stubSubscriptionUpdateFailure(500)

        intercept[Exception] {
          await(connector.updateSubscription(
            pptReference,
            createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
          ))
        }
      }

    }
  }

  "Subscription connector for display" should {

    "pass on status code and payload " when {
      "not 2xx is returned from downstream service" in {
        stubFor(get(anyUrl()).willReturn(status(417).withBody("""{"pass":"it-on"}""")))

        val result = await(connector.getSubscription("some-ref"))

        wiremock.verify(getRequestedFor(urlEqualTo("/plastic-packaging-tax/subscriptions/PPT/some-ref/display")))
        result.left.value.status mustBe 417
        result.left.value.body mustBe """{"pass":"it-on"}"""
        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }
    }
  }

  "Subscription connector for update" should {
    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      s"return $statusCode" when {
        s"$statusCode is returned from downstream service" in {

          stubSubscriptionUpdateFailure(httpStatus = statusCode)

          intercept[Exception] {
            await(connector.updateSubscription(
              pptReference,
              createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
            ))
          }
        }
      }
    }
  }

  private def stubSubscriptionDisplay(pptReference: String, response: SubscriptionDisplayResponse): Unit =
    stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(SubscriptionDisplayResponse.format.writes(response).toString())
        )
    )

  private def stubSubscriptionUpdate(
    pptReference: String,
    subscriptionProcessingDate: String,
    formBundleNumber: String
  ): Unit =
    stubFor(
      put(subscriptionUpdateUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(
              Json.obj(
                "pptReferenceNumber" -> pptReference,
                "processingDate"     -> subscriptionProcessingDate,
                "formBundleNumber"   -> formBundleNumber
              ).toString
            )
        )
    )

  private def stubSubscriptionUpdateFailure(httpStatus: Int): Any =
    stubFor(
      put(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update")
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
        )
    )

  private val subscriptionResponseBodyWithGroupMembers =
    """{
      |  "processingDate": "2022-11-01T14:12:12.981Z",
      |  "changeOfCircumstanceDetails": {
      |    "changeOfCircumstance": "Update to details"
      |  },
      |  "legalEntityDetails": {
      |    "dateOfApplication": "2021-10-13",
      |    "customerIdentification1": "01234567",
      |    "customerIdentification2": "1234567890",
      |    "customerDetails": {
      |      "customerType": "Organisation",
      |      "organisationDetails": {
      |        "organisationType": "UkCompany",
      |        "organisationName": "Test Company Ltd"
      |      }
      |    },
      |    "groupSubscriptionFlag": true,
      |    "partnershipSubscriptionFlag": false,
      |    "regWithoutIDFlag": false
      |  },
      |  "principalPlaceOfBusinessDetails": {
      |    "addressDetails": {
      |      "addressLine1": "Any Street",
      |      "addressLine2": "Any Town",
      |      "postalCode": "AA11AA",
      |      "countryCode": "GB"
      |    },
      |    "contactDetails": {
      |      "email": "ppt@mail.com",
      |      "telephone": "07712345678"
      |    }
      |  },
      |  "primaryContactDetails": {
      |    "name": "Tim Tester",
      |    "contactDetails": {
      |      "email": "contact@mail.com",
      |      "telephone": "01234567890"
      |    },
      |    "positionInCompany": "Chief Financial Officer"
      |  },
      |  "businessCorrespondenceDetails": {
      |    "addressLine1": "Business address 1",
      |    "addressLine2": "Business address 2",
      |    "postalCode": "AB1 1CD",
      |    "countryCode": "GB"
      |  },
      |  "taxObligationStartDate": "2022-04-01",
      |  "last12MonthTotalTonnageAmt": 0,
      |  "declaration": {
      |    "declarationBox1": true
      |  },
      |  "groupPartnershipSubscription": {
      |    "representativeControl": true,
      |    "allMembersControl": true,
      |    "groupPartnershipDetails": [
      |      {
      |        "relationship": "Representative",
      |        "customerIdentification1": "29078371",
      |        "customerIdentification2": "1234567890",
      |        "organisationDetails": {
      |          "organisationType": "UkCompany",
      |          "organisationName": "Test Company Ltd UK"
      |        },
      |        "individualDetails": {
      |          "firstName": "Jack",
      |          "lastName": "Gatsby"
      |        },
      |        "addressDetails": {
      |          "addressLine1": "2 Other Place",
      |          "addressLine2": "Some District",
      |          "addressLine3": "Anytown",
      |          "postalCode": "ZZ1 1ZZ",
      |          "countryCode": "GB"
      |        },
      |        "contactDetails": {
      |          "email": "ppt1@gmail.com",
      |          "telephone": "07719234679"
      |        },
      |        "regWithoutIDFlag": false
      |      },
      |      {
      |        "relationship": "Member",
      |        "customerIdentification1": "16563110",
      |        "customerIdentification2": "1234567890",
      |        "organisationDetails": {
      |          "organisationType": "UkCompany",
      |          "organisationName": "Test Company Ltd Europe"
      |        },
      |        "individualDetails": {
      |          "firstName": "Jay",
      |          "lastName": "Bond"
      |        },
      |        "addressDetails": {
      |          "addressLine1": "7 Other Place",
      |          "addressLine2": "Some District",
      |          "addressLine3": "Anytown",
      |          "postalCode": "AA11AA",
      |          "countryCode": "GB"
      |        },
      |        "contactDetails": {
      |          "email": "ppt1@gmail.com",
      |          "telephone": "07719234699"
      |        },
      |        "regWithoutIDFlag": false
      |      },
      |      {
      |        "relationship": "Member",
      |        "customerIdentification1": "37950968",
      |        "customerIdentification2": "1234567890",
      |        "organisationDetails": {
      |          "organisationType": "OverseasCompanyUkBranch",
      |          "organisationName": "Test Company Ltd Asia"
      |        },
      |        "individualDetails": {
      |          "firstName": "James",
      |          "lastName": "Bond"
      |        },
      |        "addressDetails": {
      |          "addressLine1": "12 Other Place",
      |          "addressLine2": "Some District",
      |          "addressLine3": "Anytown",
      |          "postalCode": "AA11AA",
      |          "countryCode": "GB"
      |        },
      |        "contactDetails": {
      |          "email": "ppt1@gmail.com",
      |          "telephone": "07719234799"
      |        },
      |        "regWithoutIDFlag": false
      |      }
      |    ]
      |  }
      |}""".stripMargin

}
