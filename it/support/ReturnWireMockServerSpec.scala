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

package support

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder

trait ReturnWireMockServerSpec extends ReturnsSubmissionResponseBuilder with BeforeAndAfterAll {

  this: Suite =>
  implicit lazy val server: WiremockItServer = WiremockItServer()
  private val DesSubmitReturnUrl = s"/plastic-packaging-tax/returns/PPT"
  private val nrsUrl = "/submission"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  protected def stubSubmitReturnEISRequest(pptReference: String) = {
    server.stubFor(put(s"$DesSubmitReturnUrl/$pptReference")
      .willReturn(
        ok().withBody(Json.toJson(aReturn()).toString()))
    )
  }
  protected def stubNrsRequest: Any = {
    server.stubFor(post(nrsUrl)
      .willReturn(
        aResponse()
          .withStatus(Status.ACCEPTED)
          .withBody("""{"nrSubmissionId": "nrSubmissionId"}""")
      )
    )
  }

  protected def stubNrsFailingRequest: Any = {
    server.stubFor(post(nrsUrl).willReturn(serverError().withBody("exception")))
  }
}
