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
import com.github.tomakehurst.wiremock.client.WireMock.{anyUrl, get, ok}
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{reset, when}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData.createSubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.support.ReturnTestHelper.{returnWithLegacyCreditData, returnsWithNoCreditDataJson}

import scala.concurrent.Future

class CacheItSpec extends PlaySpec with AuthTestSupport with GuiceOneServerPerSuite with BeforeAndAfterEach with BeforeAndAfterAll {

  private val wiremockServer = new WiremockItServer()

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    wiremockServer.start()
    GuiceApplicationBuilder()
      .configure(wiremockServer.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(sessionRepository))
      .build()
  }

  private lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository  = mock[SessionRepository]
  private val url                     = s"http://localhost:$port/cache/get/$pptReference"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, sessionRepository)
    wiremockServer.reset()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    wiremockServer.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    wiremockServer.stop()
  }

  "return a user answer" when {

    "credit is not available" in {
      withAuthorizedUser()
      when(sessionRepository.get(any))
        .thenReturn(Future.successful(Some(new UserAnswers("123", returnsWithNoCreditDataJson))))

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
      (response.json \ "data").as[JsObject] mustBe returnsWithNoCreditDataJson
    }

    "there are answers from previous credit journey" in {
      val answer = new UserAnswers("123", Json.parse(returnWithLegacyCreditData).as[JsObject])
      withAuthorizedUser()
      setRepository(answer)

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
      (response.json \ "data").as[JsObject] shouldBe Json.parse(newCreditJsonData).as[JsObject]
    }

    "new credit is Available" in {
      val answer = new UserAnswers("123", Json.parse(newCreditJsonData).as[JsObject])
      withAuthorizedUser()
      setRepository(answer)

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
      (response.json \ "data").as[JsObject] shouldBe Json.parse(newCreditJsonData).as[JsObject]
    }

    "return 404 if user answer not found" in {
      withAuthorizedUser()
      when(sessionRepository.get(any)).thenReturn(Future.successful(None))

      val response = await(wsClient.url(url).get())

      response.status mustBe NOT_FOUND
    }
  }

  private def setRepository(answer: UserAnswers) = {
    when(sessionRepository.get(any)).thenReturn(Future.successful(Some(answer)))
    when(sessionRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

    val subscription = createSubscriptionDisplayResponse(SubscriptionTestData.ukLimitedCompanySubscription)
    wiremockServer
      .stubFor(
        get(anyUrl())
          .willReturn(ok().withBody(Json.toJson(subscription).toString()))
      )
  }

  private def newCreditJsonData =
    """{
      |        "obligation" : {
      |            "fromDate" : "2023-01-01",
      |            "toDate" : "2023-03-31",
      |            "dueDate" : "2023-05-31",
      |            "periodKey" : "23C1"
      |        },
      |        "isFirstReturn" : false,
      |        "startYourReturn" : true,
      |        "whatDoYouWantToDo" : true,
      |        "manufacturedPlasticPackaging" : false,
      |        "manufacturedPlasticPackagingWeight" : 0,
      |        "importedPlasticPackaging" : false,
      |        "importedPlasticPackagingWeight" : 0,
      |        "directlyExportedComponents" : false,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "plasticExportedByAnotherBusiness" : false,
      |        "anotherBusinessExportWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackaging" : false,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 0,
      |        "nonExportRecycledPlasticPackaging" : false,
      |        "nonExportRecycledPlasticPackagingWeight" : 0,
      |        "credit": {
      |          "2022-06-03-2022-12-31": {
      |            "exportedCredits" : {
      |              "yesNo" : true,
      |              "weight" : 12
      |            },
      |            "convertedCredits" : {
      |              "yesNo" : true,
      |              "weight" : 34
      |            },
      |            "fromDate": "2022-06-03",
      |            "toDate": "2022-12-31"
      |          }
      |        }
      }""".stripMargin

}
