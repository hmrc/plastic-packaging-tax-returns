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

package returns

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.Mockito.reset
import org.mockito.MockitoSugar.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.json.Json.obj
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import support.WiremockItServer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.support.{AmendTestHelper, ReturnTestHelper}

import scala.concurrent.Future

class CalculationsISpec extends PlaySpec with GuiceOneServerPerSuite with AuthTestSupport with BeforeAndAfterEach with BeforeAndAfterAll {

  implicit lazy val server: WiremockItServer = WiremockItServer()
  lazy val wsClient: WSClient                = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository         = mock[SessionRepository]
  private val returnUrl = s"http://localhost:$port/returns-calculate/$pptReference"
  private val amendUrl = s"http://localhost:$port/amends-calculate/$pptReference"

  override lazy val app: Application = {
    server.start()
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .configure(server.overrideConfig)
      .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].toInstance(sessionRepository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override def afterAll(): Unit = {
    super.beforeAll()
    server.stop()
  }

  "CalculationsItController" when {
    "calculating return" should {
      "return 200 when credit is not 0" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, ReturnTestHelper.returnsWithNoCreditDataJson))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(s"http://localhost:$port/returns-calculate/$pptReference").get)

        result.status mustBe OK
      }

      "return 200 when credit is available" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, ReturnTestHelper.returnWithCreditsDataJson))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe OK
      }

      "return unauthorised" in {
        withUnauthorizedUser(new Exception)
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe UNAUTHORIZED
      }

      "return unprocessable entity when user answers is not found in the cache" in {
        withAuthorizedUser()
        when(sessionRepository.get(any)).thenReturn(Future.successful(None))

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe UNPROCESSABLE_ENTITY

      }

      "return unprocessable entity when user answer is invalid" in {
        withAuthorizedUser()
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, ReturnTestHelper.invalidReturnsDataJson))))
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe UNPROCESSABLE_ENTITY
      }

      "return the calculation json" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, ReturnTestHelper.returnWithCreditsDataJson))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.json mustBe Json.parse(
          """{"taxDue":0,"chargeableTotal":0,"deductionsTotal":315,"packagingTotal":101,"isSubmittable":false, "taxRate":0.2}"""
        )
      }

      "handle 'no credit' answers" in {
        val json = obj (
          "obligation" -> obj (
            "periodKey" -> "22C4",
            "fromDate" -> "2022-09-01",
            "toDate" -> "2022-12-31"
          ),
          "manufacturedPlasticPackagingWeight" -> 13,
          "importedPlasticPackagingWeight" -> 1,
          "exportedPlasticPackagingWeight" -> 2,
          "anotherBusinessExportWeight" -> 3,
          "nonExportedHumanMedicinesPlasticPackagingWeight" -> 4,
          "nonExportRecycledPlasticPackagingWeight" -> 5,
          "whatDoYouWantToDo" -> true,
          "exportedCredits" -> obj (
            "yesNo" -> true,
            "weight" -> 0
          ),
          "convertedCredits" -> obj (
            "yesNo" -> false,
            "weight" -> 2000
          )
        )
        when(sessionRepository.get(any)) thenReturn Future.successful(Some(UserAnswers(pptReference, json)))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe Status.OK
        result.json mustBe obj(
          "taxDue" -> 0,
          "chargeableTotal" -> 0,
          "deductionsTotal" -> 14,
          "packagingTotal" -> 14,
          "isSubmittable" -> true,
          "taxRate" -> 0.2
        )
      }
      
      "handle don't claim answer" in {
        val json = obj (
          "obligation" -> obj (
            "periodKey" -> "22C4",
            "fromDate" -> "2022-09-01",
            "toDate" -> "2022-12-31"
          ),
          "manufacturedPlasticPackagingWeight" -> 13,
          "importedPlasticPackagingWeight" -> 1,
          "exportedPlasticPackagingWeight" -> 2,
          "anotherBusinessExportWeight" -> 3,
          "nonExportedHumanMedicinesPlasticPackagingWeight" -> 4,
          "nonExportRecycledPlasticPackagingWeight" -> 5,
          "whatDoYouWantToDo" -> false,
          "exportedCredits" -> obj (
            "yesNo" -> true,
            "weight" -> 0
          ),
          "convertedCredits" -> obj (
            "yesNo" -> true,
            "weight" -> 2000
          )
        )
        when(sessionRepository.get(any)) thenReturn Future.successful(Some(UserAnswers(pptReference, json)))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(returnUrl).get)

        result.status mustBe Status.OK
        result.json mustBe obj(
          "taxDue" -> 0,
          "chargeableTotal" -> 0,
          "deductionsTotal" -> 14,
          "packagingTotal" -> 14,
          "isSubmittable" -> true,
          "taxRate" -> 0.2
        )
      }

    }

    "calculating amend" should {
      "return 200" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends))))
        withAuthorizedUser()

        val result = await(wsClient.url(amendUrl).get)

        result.status mustBe OK
      }

      "return a json" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, AmendTestHelper.userAnswersDataAmends))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(amendUrl).get)

        Json.parse(result.body) mustBe expectedAmend
      }

      "return a 500 when cache is invalid" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(amendUrl).get)

        result.status mustBe INTERNAL_SERVER_ERROR
      }

      "return a 500 when user answer is invalid" in {
        when(sessionRepository.get(any))
          .thenReturn(Future.successful(Some(UserAnswers(pptReference, AmendTestHelper.userAnswersDataWithInvalidAmends))))
        withAuthorizedUser()
        stubGetBalanceRequest

        val result = await(wsClient.url(amendUrl).get)

        result.status mustBe INTERNAL_SERVER_ERROR
      }

      "return unauthorised" in {
        withUnauthorizedUser(new Exception)

        val result = await(wsClient.url(amendUrl).get)

        result.status mustBe UNAUTHORIZED
      }
    }
  }

  private def stubGetBalanceRequest =
    server.stubFor(
      get(urlPathEqualTo(s"/plastic-packaging-tax/export-credits/PPT/$pptReference"))
        .willReturn(ok().withBody(Json.toJson(ReturnTestHelper.createCreditBalanceDisplayResponse).toString()))
    )

  private def expectedAmend = Json.parse(
    """{"original":{
      | "taxDue":44,
      | "chargeableTotal":220,
      | "deductionsTotal":0,
      | "packagingTotal":250,
      | "isSubmittable":true,
      | "taxRate":0.2
      | },
      | "amend":{
      |   "taxDue":17.2,
      |   "chargeableTotal":86,
      |   "deductionsTotal":15,
      |   "packagingTotal":101,
      |   "isSubmittable":true,
      |   "taxRate":0.2
      | }
      |}""".stripMargin
  )
}
