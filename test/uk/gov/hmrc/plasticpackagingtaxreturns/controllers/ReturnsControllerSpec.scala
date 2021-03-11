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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status, _}

import scala.concurrent.Future

class ReturnsControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .build()

  override def beforeEach(): Unit =
    super.beforeEach()

  "GET /:id" should {
    val get = FakeRequest("GET", "/returns")

    "return 200" when {
      "request is valid" in {
        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsString(result) mustBe "OK"
      }
    }
  }
}
