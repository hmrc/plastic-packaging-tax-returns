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

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{OK, contentAsJson, defaultAwaitTimeout, route, status, _}
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.TileInfoController
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTObligations

import java.util.UUID
import scala.concurrent.Future

class TileInfoControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  lazy val sut: TileInfoController = inject[TileInfoController]
  val testPPTReference: String = UUID.randomUUID().toString

  "get" must {
    val request = FakeRequest("GET", "/obligations/open/" + testPPTReference)

    "be accessible from the requestHandler" in {
      val result: Future[Result] = route(app, request).get

      status(result) must not be NOT_FOUND
    }

    "return 200 code" in {
      val result:Future[Result] = sut.get(testPPTReference)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(PPTObligations(true))
    }
  }

}
