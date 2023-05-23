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
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.support.ReturnTestHelper.{returnWithLegacyCreditData, returnsWithNoCreditDataJson}

import scala.concurrent.Future


class CacheItSpec extends PlaySpec with AuthTestSupport with GuiceOneServerPerSuite with BeforeAndAfterEach{

  private lazy val wsClient: WSClient        = app.injector.instanceOf[WSClient]
  private lazy val sessionRepository = mock[SessionRepository]
  private val url = s"http://localhost:$port/cache/get/$pptReference"

  override lazy val app: Application = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder()
      .overrides(bind[AuthConnector].to(mockAuthConnector),
        bind[SessionRepository].to(sessionRepository))
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, sessionRepository)
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

    "old credit is Available" in {
      val answer = new UserAnswers("123", Json.parse(returnWithLegacyCreditData).as[JsObject])
      withAuthorizedUser()
      setRepository(answer)

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
      (response.json \ "data").as[JsObject]  shouldBe Json.parse(newCreditJsonData).as[JsObject]
    }

    "new credit is Available" in {
      val answer = new UserAnswers("123", Json.parse(newCreditJsonData).as[JsObject])
      withAuthorizedUser()
      setRepository(answer)

      val response = await(wsClient.url(url).get())

      response.status mustBe OK
      (response.json \ "data").as[JsObject]  shouldBe Json.parse(newCreditJsonData).as[JsObject]
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
  }

  def newCreditJsonData =
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
  |        "credit": {
  |          "2022-04-01-2022-12-31": {
  |            "exportedCredits" : {
  |              "yesNo" : true,
  |              "weight" : 12
  |            },
  |            "convertedCredits" : {
  |              "yesNo" : true,
  |              "weight" : 34
  |            },
  |            "fromDate": "2022-04-01",
  |            "endDate": "2022-12-31"
  |          }
  |        },
  |        "creditAvailableYears" : [
  |            {
  |                "from" : "2022-04-01",
  |                "to" : "2022-12-31"
  |            }
  |        ],
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
  |        "nonExportRecycledPlasticPackagingWeight" : 0
  |    }""".stripMargin
}
