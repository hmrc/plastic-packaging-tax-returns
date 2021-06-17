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
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.`given`
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
import play.api.test.Helpers.{route, status, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.SubscriptionsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import scala.concurrent.Future

class SubscriptionControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with AuthTestSupport with SubscriptionTestData {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SubscriptionsConnector].to(subscriptionsConnector))
    .build()

  private val subscriptionsConnector: SubscriptionsConnector = mock[SubscriptionsConnector]

  private def getRequest(pptReference: String) = FakeRequest("GET", s"/subscriptions/$pptReference")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(subscriptionsConnector)
  }

  "GET subscription" should {

    "return 200" when {
      "request for uk limited company subscription is valid" in {
        withAuthorizedUser()
        val ltdSubscription = ukLimitedCompanyPptSubscription(pptReference)
        given(subscriptionsConnector.getSubscription(ArgumentMatchers.eq(pptReference))(any[HeaderCarrier])).willReturn(
          Future.successful(Right(ltdSubscription))
        )

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(ltdSubscription)
      }
    }

    "return 200" when {
      "request for sole trader subscription is valid" in {
        withAuthorizedUser()
        val soleTraderSubscription = soleTraderPptSubscription(pptReference)
        given(subscriptionsConnector.getSubscription(ArgumentMatchers.eq(pptReference))(any[HeaderCarrier])).willReturn(
          Future.successful(Right(soleTraderSubscription))
        )

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(soleTraderSubscription)
      }
    }

    "return 404" when {
      "registration not found" in {
        withAuthorizedUser()
        given(subscriptionsConnector.getSubscription(ArgumentMatchers.eq(pptReference))(any[HeaderCarrier])).willReturn(
          Future.successful(Left(404))
        )

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) mustBe NOT_FOUND
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val result: Future[Result] = route(app, getRequest(pptReference)).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(subscriptionsConnector)
      }
    }
  }
}
