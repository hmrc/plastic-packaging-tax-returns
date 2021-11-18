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

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty, OK}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.{MockConnectors, MockReturnsRepository}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.{ReturnsSubmissionResponseBuilder, TaxReturnBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository

import scala.concurrent.Future

class ReturnsSubmissionControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with SubscriptionTestData with MockConnectors with MockReturnsRepository with TaxReturnBuilder
    with ReturnsSubmissionResponseBuilder {

  SharedMetricRegistries.clear()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
  }

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector),
               bind[ReturnsConnector].to(mockReturnsConnector),
               bind[TaxReturnRepository].to(mockReturnsRepository)
    )
    .build()

  "Returns submission controller" should {
    "submit a return via the returns connector" in {
      withAuthorizedUser()
      mockGetReturn(Some(aTaxReturn()))

      val returnsSubmissionResponse = aReturnsSubmissionResponse()
      mockReturnsSubmissionConnector(returnsSubmissionResponse)

      val submitReturnRequest = FakeRequest("POST", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe OK
      contentAsJson(result) mustBe toJson(returnsSubmissionResponse)
    }

    "return not found (404) if return not found in repository" in {
      withAuthorizedUser()
      mockGetReturn(None)

      val submitReturnRequest = FakeRequest("POST", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe NOT_FOUND
    }

    "propagate status code when failure occurs" in {
      withAuthorizedUser()
      mockGetReturn(Some(aTaxReturn()))
      mockReturnsSubmissionConnectorFailure(BAD_REQUEST)

      val submitReturnRequest = FakeRequest("POST", s"/returns-submission/$pptReference")

      val result: Future[Result] = route(app, submitReturnRequest).get

      status(result) mustBe BAD_REQUEST
    }
  }
}
