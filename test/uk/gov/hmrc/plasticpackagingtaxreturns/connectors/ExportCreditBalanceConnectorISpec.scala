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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError

import java.time.LocalDate

class ExportCreditBalanceConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  lazy val connector: ExportCreditBalanceConnector = app.injector.instanceOf[ExportCreditBalanceConnector]

  val displayExportCreditBalanceTimer = "ppt.exportcreditbalance.display.timer"

  val pptReference        = "XXPPTP103844123"
  val fromDate: LocalDate = LocalDate.parse("2021-10-01")
  val toDate: LocalDate   = LocalDate.parse("2021-10-31")

  val exportCreditBalanceDisplayResponse: ExportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  "ExportCreditBalance connector" when {
    "requesting a balance" should {
      "handle a 200 with credit balance data" in {

        stubExportCreditBalanceDisplay(exportCreditBalanceDisplayResponse)

        val res = await(connector.getBalance(pptReference, fromDate, toDate))
        res.right.get mustBe exportCreditBalanceDisplayResponse

        getTimer(displayExportCreditBalanceTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        stubFor(
          get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        val res = await(connector.getBalance(pptReference, fromDate, toDate))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
        getTimer(displayExportCreditBalanceTimer).getCount mustBe 1
      }

    }
  }

  "ExportCreditBalance connector for display" should {
    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {
        statusCode + " is returned from downstream service" in {
          stubExportCreditBalanceDisplayFailure(httpStatus = statusCode,
                                                errors = Seq(EISError("Error Code", "Error Reason"))
          )

          val res = await(connector.getBalance(pptReference, fromDate, toDate))

          res.left.get mustBe statusCode
          getTimer(displayExportCreditBalanceTimer).getCount mustBe 1
        }
      }
    }
  }

  private def stubExportCreditBalanceDisplay(response: ExportCreditBalanceDisplayResponse): Unit =
    stubFor(
      get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(ExportCreditBalanceDisplayResponse.format.writes(response).toString())
        )
    )

  private def stubExportCreditBalanceDisplayFailure(httpStatus: Int, errors: Seq[EISError]): Any =
    stubFor(
      get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate")
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
            .withBody(Json.obj("failures" -> errors).toString)
        )
    )

}
