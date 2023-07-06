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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.reset
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData.{createSubscriptionDisplayResponse, soleTraderSubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

class SubscriptionItSpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with AuthTestSupport
    with BeforeAndAfterAll
    with BeforeAndAfterEach{

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val wireMock: WiremockItServer = WiremockItServer()
  private lazy val sessionRepository = mock[SessionRepository]

  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]
  val successfulDisplayResponse = Json.toJson(createSubscriptionDisplayResponse(soleTraderSubscription))

  override lazy val app: Application = {
    wireMock.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(wireMock.overrideConfig)
      .overrides(
        bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(sessionRepository)
      )
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

      val response = await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      response.status mustBe OK
    }

    "return a json display response" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      val response = await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      response.json mustBe successfulDisplayResponse
    }

    "return an error" in {
      withAuthorizedUser()
      stubSubscriptionErrorDisplay

      val response = await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      response.status mustBe NOT_FOUND
      response.json.toString() mustBe """{"a":"test-a","b":"test-b"}"""

    }

    "handle bad json" in {
      withAuthorizedUser()
      stubSubscriptionDisplay("bad json")

      val response = await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      response.status mustBe INTERNAL_SERVER_ERROR
    }

    "retry 3 times" in {
      withAuthorizedUser()
      stubSubscriptionErrorDisplay

      await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      wireMock.verify(3, getRequestedFor(
        urlEqualTo(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"))
      )
    }

    "retry 1 time" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      wireMock.verify(1, getRequestedFor(
        urlEqualTo(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"))
      )
    }
    "use a eis bearer token" in {
      withAuthorizedUser()
      stubSubscriptionDisplay(successfulDisplayResponse.toString())

      await(wsClient.url(s"http://localhost:$port/subscriptions/$pptReference").get())

      wireMock.verify(getRequestedFor(
        urlEqualTo(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo("Bearer eis-test123456"))
      )
    }
  }

  def stubSubscriptionDisplay(body: String) = {
    wireMock.stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(ok().withBody(body)
        )
    )
  }

  def stubSubscriptionErrorDisplay = {

    wireMock.stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(notFound().withBody({"""{"a": "test-a", "b": "test-b"}"""})
        )
    )
  }
}
