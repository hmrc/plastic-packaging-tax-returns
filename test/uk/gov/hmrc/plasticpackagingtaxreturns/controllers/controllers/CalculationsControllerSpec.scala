/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.UNPROCESSABLE_ENTITY
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, contentAsJson, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import scala.concurrent.Future

class CalculationsControllerSpec
  extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures
    with Matchers with AuthTestSupport with MockConnectors with ReturnsSubmissionResponseBuilder {

  SharedMetricRegistries.clear()

  private val userAnswersData = Json.parse(
    """{
      |        "obligation" : {
      |            "periodKey" : "21C4"
      |        },
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
      |        "nonExportRecycledPlasticPackagingWeight" : 5
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val invalidUserAnswersData = Json.parse(
    """{
      |        "obligation" : {
      |            "periodKey" : "21C4"
      |        },
      |        "manufacturedPlasticPackagingWeight" : 100,
      |        "importedPlasticPackagingWeight" : 0,
      |        "exportedPlasticPackagingWeight" : 0,
      |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10
      |    }""".stripMargin).asInstanceOf[JsObject]

  private val userAnswers: UserAnswers        = UserAnswers("id").copy(data = userAnswersData)
  private val invalidUserAnswers: UserAnswers = UserAnswers("id").copy(data = invalidUserAnswersData)

  private val mockAppConfig: AppConfig                 = mock[AppConfig]
  private val mockSessionRepository: SessionRepository = mock[SessionRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    reset(mockSessionRepository)
  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].to(mockAuthConnector),
      bind[SessionRepository].to(mockSessionRepository),
      bind[AppConfig].to(mockAppConfig)
    )
    .build()

  "Calculations controller" when {

    "has valid user answers" should {

      "return OK response" in {

        withAuthorizedUser()

        setupMocks(userAnswers)

        val expected: Calculations = Calculations(taxDue = 17, chargeableTotal = 85, deductionsTotal = 15, packagingTotal = 100, isSubmittable = true)

        val submitReturnRequest = FakeRequest("GET", s"/returns-calculate/$pptReference")

        val result: Future[Result] = route(app, submitReturnRequest).get

        status(result) mustBe OK
        contentAsJson(result) mustBe toJson(expected)
      }

    }

    "has an incomplete cache" should {

      "return un-processable entity" in {

        withAuthorizedUser()

        setupMocks(invalidUserAnswers)

        val submitReturnRequest = FakeRequest("GET", s"/returns-calculate/$pptReference")

        val result: Future[Result] = route(app, submitReturnRequest).get

        status(result) mustBe UNPROCESSABLE_ENTITY

      }

    }

  }

  private def setupMocks(uA: UserAnswers) = {
    when(mockAppConfig.taxRatePoundsPerKg).thenReturn(BigDecimal(0.20))
    when(mockSessionRepository.get(any[String])).thenReturn(Future.successful(Some(uA)))
  }

}
