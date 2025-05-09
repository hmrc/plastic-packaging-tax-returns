/*
 * Copyright 2025 HM Revenue & Customs
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
 * Copyright 2025 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData.{createSubscriptionDisplayResponse, ukLimitedCompanyGroupSubscription}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.{ReturnObligationFromDateGettable, ReturnObligationToDateGettable}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Settable.SettableUserAnswers
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.LocalDate
import scala.concurrent.Future

class AvailableCreditDateRangesControllerItSpec
    extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with BeforeAndAfterAll with BeforeAndAfterEach {

  val httpClient: DefaultHttpClient          = app.injector.instanceOf[DefaultHttpClient]
  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository         = mock[SessionRepository]

  private val url = s"http://localhost:$port/credits/available-years/$pptReference"

  private val userAnswers = UserAnswers("user-answers-id")
    .setUnsafe(ReturnObligationFromDateGettable, LocalDate.of(2022, 5, 1))
    .setUnsafe(ReturnObligationToDateGettable, LocalDate.of(2023, 5, 1))

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(sessionRepository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    server.reset()

    withAuthorizedUser()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  "it" should {
    "return 200 date range" in {
      stubSubscriptionDisplay
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(userAnswers)))

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
    }

    "return date range" in {
      stubSubscriptionDisplay
      when(sessionRepository.get(any)).thenReturn(Future.successful(Some(userAnswers)))

      val response = await(wsClient.url(url).get())

      response.json mustBe Json.toJson(Seq(CreditRangeOption(LocalDate.of(2022, 6, 3), LocalDate.of(2023, 3, 31))))
    }

    "return unprosessable status" in {
      when(sessionRepository.get(any)).thenReturn(Future.successful(None))

      val response = await(wsClient.url(url).get())

      response.status mustBe UNPROCESSABLE_ENTITY
      response.body mustBe "No user answers found"
    }

    "return a 500" when {
      "date in user answer are missing" in {
        stubSubscriptionDisplay

        val ans = UserAnswers("user-answers-id")
          .setUnsafe(ReturnObligationFromDateGettable, LocalDate.of(2022, 5, 1))
        when(sessionRepository.get(any)).thenReturn(Future.successful(Some(ans)))

        val response = await(wsClient.url(url).get())

        response.status mustBe INTERNAL_SERVER_ERROR
      }

      "subscription API return Throws" in {
        stubSubscriptionDisplayError
        when(sessionRepository.get(any)).thenReturn(Future.successful(Some(userAnswers)))

        val response = await(wsClient.url(url).get())

        response.status mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  private def stubSubscriptionDisplay =
    server.stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(createSubscriptionDisplayResponse(ukLimitedCompanyGroupSubscription)).toString())
        )
    )

  private def stubSubscriptionDisplayError =
    server.stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
        )
    )

}
