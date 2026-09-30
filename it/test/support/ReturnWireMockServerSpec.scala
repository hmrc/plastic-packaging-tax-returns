/*
 * Copyright 2026 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status
import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  Obligation,
  ObligationDataResponse,
  ObligationDetail
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.HipReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.ReturnsSubmissionResponseBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.support.ReturnTestHelper

trait ReturnWireMockServerSpec extends ReturnsSubmissionResponseBuilder with BeforeAndAfterAll {

  this: Suite =>
  implicit lazy val wireMock: WiremockItServer = WiremockItServer()
  private val DesSubmitReturnUrl               = s"/plastic-packaging-tax/returns/PPT"
  private val HipSubmitReturnUrl               = s"/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT"
  private val nrsUrl                           = "/submission"
  private val balanceHipURL                    = s"/etmp/RESTAdapter/plastic-packaging-tax/export-credits/PPT"
  private val balanceEISURL                    = s"/plastic-packaging-tax/export-credits/PPT/"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    wireMock.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    wireMock.stop()
  }

  protected def stubSubmitReturnEISRequest(pptReference: String) =
    wireMock.stubFor(
      put(s"$DesSubmitReturnUrl/$pptReference")
        .willReturn(ok().withBody(Json.toJson(aReturn()).toString()))
    )

  protected def stubSubmitReturnHipRequest(pptReference: String) =
    wireMock.stubFor(
      put(s"$HipSubmitReturnUrl/$pptReference")
        .willReturn(ok().withBody(Json.toJson(HipReturn(aReturn())).toString()))
    )

  protected def stubNrsRequest: Any =
    wireMock.stubFor(
      post(nrsUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.ACCEPTED)
            .withBody("""{"nrSubmissionId": "nrSubmissionId"}""")
        )
    )

  protected def stubNrsFailingRequest: Any =
    wireMock.stubFor(post(nrsUrl).willReturn(serverError().withBody("exception")))

  protected def stubObligationDesRequest(pptReference: String, status: Int = Status.OK): StubMapping = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation]        = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse]      = Json.writes[ObligationDataResponse]
    val obligationDesRequest                         = s"/enterprise/obligation-data/zppt/$pptReference/PPT?status=O"
    wireMock.stubFor(
      get(obligationDesRequest)
        .willReturn(
          aResponse
            .withStatus(status)
            .withBody(Json.toJson(ObligationSpecHelper.createOneObligation(pptReference))(writes).toString())
        )
    )
  }

  protected def stubGetBalanceEISRequest(pptReference: String): StubMapping =
    wireMock.stubFor(
      get(urlPathEqualTo(s"$balanceEISURL/$pptReference"))
        .willReturn(ok().withBody(Json.toJson(ReturnTestHelper.createCreditBalanceDisplayResponse).toString()))
    )

  protected def stubGetBalanceHipRequest(pptReference: String): StubMapping =
    wireMock.stubFor(
      get(urlPathEqualTo(s"$balanceHipURL/$pptReference"))
        .willReturn(ok().withBody(Json.toJson(ReturnTestHelper.createCreditBalanceDisplayResponse).toString()))
    )

}
