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

import java.time.LocalDate

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.Mockito.{reset, verifyNoInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, contentAsJson, route, status, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{EnterpriseTestData, SubscriptionTestData}

import scala.concurrent.Future

class FinancialDataControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with SubscriptionTestData with MockConnectors with EnterpriseTestData {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector), bind[FinancialDataConnector].to(mockFinancialDataConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(mockFinancialDataConnector)
  }

  val fromDate: LocalDate = LocalDate.parse("2021-10-01")
  val toDate: LocalDate   = LocalDate.parse("2021-10-31")
  val baseUrl             = s"/financial-data/$pptReference?fromDate=$fromDate&toDate=$toDate"

  private def getRequest(url: String) =
    FakeRequest("GET", url)

  "GET financial data" should {

    "return 200" when {
      "request for financial data is valid" in {
        withAuthorizedUser()
        mockGetFinancialData(pptReference, financialDataResponse)

        val result: Future[Result] =
          route(app, getRequest(baseUrl)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(financialDataResponse)
      }

      "request for financial data has optional parameters" in {
        withAuthorizedUser()
        mockGetFinancialData(pptReference, financialDataResponse)

        val result: Future[Result] =
          route(
            app,
            getRequest(
              baseUrl + s"&onlyOpenItems=true&includeLocks=false&calculateAccruedInterest=false&customerPaymentInformation=true"
            )
          ).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(financialDataResponse)
      }
    }

    "return 400" when {
      "request for obligation data is invalid" in {
        withAuthorizedUser()
        mockGetFinancialData(pptReference, financialDataResponse)

        val result: Future[Result] =
          route(app, FakeRequest("GET", s"/financial-data/$pptReference?fromDate=invalid&toDate=invalid")).get

        status(result) must be(BAD_REQUEST)
      }
    }

    "return 404" when {
      "request for obligation data fails" in {
        withAuthorizedUser()
        mockGetFinancialDataFailure(pptReference, 404)

        val result: Future[Result] =
          route(app, getRequest(baseUrl)).get

        status(result) mustBe NOT_FOUND
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val result: Future[Result] =
          route(app, getRequest(baseUrl)).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockObligationDataConnector)
      }
    }

  }
}