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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{
  NonRepudiationMetadata,
  NonRepudiationSubmissionAccepted
}

class NonRepudiationConnectorISpec
    extends ConnectorISpec with Injector with AuthTestSupport with NrsTestData with ScalaFutures {
  lazy val config                             = Map("microservice.services.nrs.api-key" -> testNonRepudiationApiKey)
  lazy val connector: NonRepudiationConnector = app.injector.instanceOf[NonRepudiationConnector]
  private implicit val testNonRepudiationApiKey: String = "test-key"

  private val pptNrsSubmissionTimer = "ppt.nrs.submission.timer"

  "submitNonRepudiation" should {
    val testEncodedPayload  = "testEncodedPayload"
    val testPayloadChecksum = "testPayloadChecksum"
    val headerData          = Map("testHeaderKey" -> "testHeaderValue")

    val testNonRepudiationMetadata = NonRepudiationMetadata(
      businessId = "vrs",
      notableEvent = "vat-registration",
      payloadContentType =
        "application/json",
      payloadSha256Checksum =
        testPayloadChecksum,
      userSubmissionTimestamp =
        testDateTimeString,
      identityData =
        testNonRepudiationIdentityData,
      userAuthToken = testAuthToken,
      headerData = headerData,
      searchKeys =
        Map("postCode" -> testPPTReference)
    )

    val expectedRequestJson: JsObject =
      Json.obj("payload" -> testEncodedPayload, "metadata" -> testNonRepudiationMetadata)

    "return a success" in {
      val testNonRepudiationSubmissionId = "testNonRepudiationSubmissionId"
      stubNonRepudiationSubmission(
        ACCEPTED,
        expectedRequestJson,
        Json.obj("nrSubmissionId" -> testNonRepudiationSubmissionId)
      )

      val res = connector.submitNonRepudiation(testEncodedPayload, testNonRepudiationMetadata)

      await(res) mustBe NonRepudiationSubmissionAccepted(testNonRepudiationSubmissionId)
      getTimer(pptNrsSubmissionTimer).getCount mustBe 1
    }

    "throw an exception" when {
      "nrs service fails" in {
        stubNonRepudiationSubmissionFailure(INTERNAL_SERVER_ERROR, expectedRequestJson)

        intercept[Exception] {
          await(connector.submitNonRepudiation(testEncodedPayload, testNonRepudiationMetadata))
        }
        getTimer(pptNrsSubmissionTimer).getCount mustBe 4 // We have 3 retries configured
      }
    }

    "retry" when {
      "nrs submission fails on first attempt" in {
        val expectedSubmissionId = "nrs-submission-id"
        expectNRSToFailOnceAndThenSucceed(
          INTERNAL_SERVER_ERROR,
          ACCEPTED,
          expectedRequestJson,
          Json.obj("nrSubmissionId" -> expectedSubmissionId)
        )

        await(connector.submitNonRepudiation(testEncodedPayload, testNonRepudiationMetadata))
          .submissionId must be(expectedSubmissionId)
      }
    }

  }

  private def stubNonRepudiationSubmission(status: Int, request: JsValue, response: JsObject)(implicit
    apiKey: String
  ): StubMapping =
    stubFor(
      post(urlMatching(s"/submission"))
        .withRequestBody(equalToJson(request.toString()))
        .withHeader("X-API-Key", equalTo(apiKey))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response.toString())
        )
    )

  private def stubNonRepudiationSubmissionFailure(status: Int, requestJson: JsValue)(implicit
    apiKey: String
  ): StubMapping =
    stubFor(
      post(urlMatching(s"/submission"))
        .withRequestBody(equalToJson(requestJson.toString()))
        .withHeader("X-API-Key", equalTo(apiKey))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  private def expectNRSToFailOnceAndThenSucceed(
    failStatus: Int,
    successStatus: Int,
    request: JsValue,
    response: JsObject
  )(implicit apiKey: String): StubMapping = {
    val scenario = "Retry Scenario"
    stubFor(
      post(urlMatching(s"/submission"))
        .withRequestBody(equalToJson(request.toString()))
        .withHeader("X-API-Key", equalTo(apiKey))
        .inScenario(scenario)
        .whenScenarioStateIs("Started")
        .willReturn(
          aResponse()
            .withStatus(failStatus)
        )
        .willSetStateTo("One Failure")
    )

    stubFor(
      post(urlMatching(s"/submission"))
        .withRequestBody(equalToJson(request.toString()))
        .withHeader("X-API-Key", equalTo(apiKey))
        .inScenario(scenario)
        .whenScenarioStateIs("One Failure")
        .willReturn(
          aResponse()
            .withStatus(successStatus)
            .withBody(response.toString())
        )
    )
  }

}
