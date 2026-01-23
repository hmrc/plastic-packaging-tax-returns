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

package test

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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.http.{HeaderNames, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateWithNrsSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.NrsTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData.{createSubscriptionDisplayResponse, createSubscriptionUpdateRequest, soleTraderSubscription, ukLimitedCompanySubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.nonRepudiation.NonRepudiationService

import java.time.{ZoneOffset, ZonedDateTime}

class SubscriptionItSpec
    extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with NrsTestData with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit lazy val wireMock: WiremockItServer = WiremockItServer()
  private lazy val wsClient: WSClient          = app.injector.instanceOf[WSClient]

  private val successfulDisplayResponse = Json.toJson(createSubscriptionDisplayResponse(soleTraderSubscription))
  private val subscriptionUrl           = s"http://localhost:$port/subscriptions/$pptReference"
  private val eisSubscriptionDisplayUrl = s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"
  private val eisSubscriptionUpdateUrl  = s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update"

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(mock[SessionRepository]))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    wireMock.reset()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    wireMock.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    wireMock.stop()
  }

  "Get" should {
    "return 200" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      val response = await(wsClient.url(subscriptionUrl).get())

      response.status mustBe OK
    }

    "return a json display response" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      val response = await(wsClient.url(subscriptionUrl).get())

      response.json mustBe successfulDisplayResponse
    }

    "return an error" in {
      withAuthorizedUser()
      stubSubscriptionErrorDisplay

      val response = await(wsClient.url(subscriptionUrl).get())

      response.status mustBe NOT_FOUND
      response.json.toString() mustBe """{"a":"test-a","b":"test-b"}"""

    }

    "handle bad json" in {
      withAuthorizedUser()
      stubSubscriptionDisplay("bad json")

      val response = await(wsClient.url(subscriptionUrl).get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "retry 3 times" in {
      withAuthorizedUser()
      stubSubscriptionErrorDisplay

      await(wsClient.url(subscriptionUrl).get())

      wireMock.verify(3, getRequestedFor(urlEqualTo(eisSubscriptionDisplayUrl)))
    }

    "retry 1 time" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      await(wsClient.url(subscriptionUrl).get())

      wireMock.verify(1, getRequestedFor(urlEqualTo(eisSubscriptionDisplayUrl)))
    }
    "use a eis bearer token" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      await(wsClient.url(subscriptionUrl).get())

      wireMock.verify(
        getRequestedFor(urlEqualTo(eisSubscriptionDisplayUrl))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
      )
    }
  }

  "put" should {

    val updateDetails = createSubscriptionUpdateRequest(ukLimitedCompanySubscription)

    "return 200" in {
      val processingDate = ZonedDateTime.now(ZoneOffset.UTC)
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubscriptionUpdate(processingDate, "123")
      stubNonRepudiationSubmission

      val response = await(updateSubscription(updateDetails))

      response.status mustBe OK

      withClue("return a json") {
        response.json mustBe Json.toJson(
          SubscriptionUpdateWithNrsSuccessfulResponse(pptReference, processingDate, "123", "testNonRepudiationSubmissionId")
        )
      }
    }

    "retry 3 times" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubscriptionUpdateError()
      stubNonRepudiationSubmission

      await(updateSubscription(updateDetails))

      wireMock.verify(3, putRequestedFor(urlEqualTo(eisSubscriptionUpdateUrl)))
    }

    "retry 1 times" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubscriptionUpdate(ZonedDateTime.now(ZoneOffset.UTC), "123")
      stubNonRepudiationSubmission

      await(updateSubscription(updateDetails))

      wireMock.verify(1, putRequestedFor(urlEqualTo(eisSubscriptionUpdateUrl)))
    }

    "use a eis bearer token" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubscriptionUpdateError()

      await(updateSubscription(updateDetails))

      wireMock.verify(
        putRequestedFor(urlEqualTo(eisSubscriptionUpdateUrl))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
      )
    }

    "return an error if subscription throw an error" in {
      withAuthorizedUser()
      mockAuthorization(NonRepudiationService.nonRepudiationIdentityRetrievals, testAuthRetrievals)
      stubSubscriptionUpdateError()

      val response = await(updateSubscription(updateDetails))

      response.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def updateSubscription(updateDetails: SubscriptionUpdateRequest) =
    wsClient.url(subscriptionUrl).addHttpHeaders("Authorization" -> "TOKEN")
      .put(Json.toJson(updateDetails))

  def stubSubscriptionDisplay(body: String) =
    wireMock.stubFor(get(eisSubscriptionDisplayUrl).willReturn(ok().withBody(body)))

  def stubSubscriptionErrorDisplay =
    wireMock.stubFor(get(eisSubscriptionDisplayUrl).willReturn(notFound().withBody("""{"a": "test-a", "b": "test-b"}""")))

  private def stubSubscriptionUpdate(processingDate: ZonedDateTime, formBuildNUmber: String): Unit =
    wireMock.stubFor(
      put(eisSubscriptionUpdateUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(
              Json.obj(
                "pptReferenceNumber" -> pptReference,
                "processingDate"     -> processingDate.toString,
                "formBundleNumber"   -> formBuildNUmber
              ).toString
            )
        )
    )

  private def stubSubscriptionUpdateError(): Unit =
    wireMock.stubFor(put(eisSubscriptionUpdateUrl).willReturn(serverError().withBody("error")))

  private def stubNonRepudiationSubmission: StubMapping =
    wireMock.stubFor(
      post(s"/submission")
        .withRequestBody(matchingJsonPath("payload"))
        .withHeader("X-API-Key", equalTo("test-key"))
        .willReturn(
          aResponse()
            .withStatus(202)
            .withBody(Json.obj("nrSubmissionId" -> "testNonRepudiationSubmissionId").toString())
        )
    )

}
