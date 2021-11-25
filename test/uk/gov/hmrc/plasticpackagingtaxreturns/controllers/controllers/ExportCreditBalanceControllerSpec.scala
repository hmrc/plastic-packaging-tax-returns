/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ExportCreditBalanceConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockConnectors
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import scala.concurrent.Future

class ExportCreditBalanceControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with SubscriptionTestData with MockConnectors {

  SharedMetricRegistries.clear()

  val getResponse = ExportCreditBalanceDisplayResponse(processingDate = "2021-11-17T09:32:50.345Z",
                                                       totalPPTCharges = BigDecimal(1000),
                                                       totalExportCreditClaimed = BigDecimal(100),
                                                       totalExportCreditAvailable = BigDecimal(200)
  )

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector),
               bind[ExportCreditBalanceConnector].to(mockExportCreditBalanceConnector)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(mockExportCreditBalanceConnector)
  }

  private def getRequest(
    pptReference: String,
    fromDate: LocalDate = LocalDate.parse("2021-10-01"),
    toDate: LocalDate = LocalDate.parse("2021-10-31")
  ) =
    FakeRequest("GET", s"/export-credits/$pptReference/?fromDate=$fromDate&toDate=$toDate")

  "GET balance" should {

    "return 200" when {
      "request for export credit balance is valid" in {
        withAuthorizedUser()
        mockGetExportCreditBalance(pptReference, getResponse)

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(getResponse)
      }
    }

    "return 400" when {
      "request for export credit balance is invalid" in {
        withAuthorizedUser()
        mockGetExportCreditBalance(pptReference, getResponse)

        val result: Future[Result] =
          route(app, FakeRequest("GET", s"/export-credits/$pptReference?fromDate=invalid&toDate=invalid")).get

        status(result) must be(BAD_REQUEST)
      }
    }

    "return 404" when {
      "request for export credit balance fails" in {
        withAuthorizedUser()
        mockGetExportCreditBalanceFailure(pptReference, 404)

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) mustBe NOT_FOUND
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockExportCreditBalanceConnector)
      }
    }

  }
}