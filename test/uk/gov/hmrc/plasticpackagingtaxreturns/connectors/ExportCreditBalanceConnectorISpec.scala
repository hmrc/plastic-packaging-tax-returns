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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError

import java.time.LocalDate

class ExportCreditBalanceConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  lazy val connector: ExportCreditBalanceConnector = app.injector.instanceOf[ExportCreditBalanceConnector]

  val displayExportCreditBalanceTimer = "ppt.exportcreditbalance.display.timer"

  val internalId: String       = "someId"
  val pptReference: String     = "XXPPTP103844123"
  val fromDate: LocalDate      = LocalDate.parse("2021-10-01")
  val toDate: LocalDate        = LocalDate.parse("2021-10-31")
  val auditUrl: String         = "/write/audit"
  val implicitAuditUrl: String = s"$auditUrl/merged"

  val exportCreditBalanceDisplayResponse: ExportCreditBalanceDisplayResponse = ExportCreditBalanceDisplayResponse(
    processingDate = "2021-11-17T09:32:50.345Z",
    totalPPTCharges = BigDecimal(1000),
    totalExportCreditClaimed = BigDecimal(100),
    totalExportCreditAvailable = BigDecimal(200)
  )

  override def overrideConfig: Map[String, Any] =
    Map("microservice.services.eis.host" -> wireHost,
      "microservice.services.eis.port" -> wirePort,
      "auditing.consumer.baseUri.port" -> wirePort,
      "auditing.enabled" -> true
    )

  "ExportCreditBalance connector" when {

    "requesting a balance" should {

      "handle a 200 with credit balance data" in {

        val auditModel = GetExportCredits(internalId, pptReference, fromDate, toDate, "Success", Some(exportCreditBalanceDisplayResponse), None)

        stubExportCreditBalanceDisplay(exportCreditBalanceDisplayResponse)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(connector.getBalance(pptReference, fromDate, toDate, internalId))
        res.right.get mustBe exportCreditBalanceDisplayResponse

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(displayExportCreditBalanceTimer).getCount mustBe 1

      }

      "handle bad json" in {

        val url    = s"http://localhost:20202/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate"

        val error = s"GET of '$url' returned invalid json. Attempting to convert to " +
          s"uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse gave errors: " +
          s"List((/totalPPTCharges,List(JsonValidationError(List(error.path.missing),WrappedArray()))), " +
          s"(/totalExportCreditAvailable,List(JsonValidationError(List(error.path.missing),WrappedArray()))), " +
          s"(/processingDate,List(JsonValidationError(List(error.path.missing),WrappedArray()))), " +
          s"(/totalExportCreditClaimed,List(JsonValidationError(List(error.path.missing),WrappedArray()))))"

        val auditModel = GetExportCredits(internalId, pptReference, fromDate, toDate, "Failure", None, Some(error))

        stubFor(
          get(s"/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(connector.getBalance(pptReference, fromDate, toDate, internalId))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(displayExportCreditBalanceTimer).getCount mustBe 1

      }

    }

    "ExportCreditBalance connector for display" should {

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>

        "return " + statusCode when {

          statusCode + " is returned from downstream service" in {

            val url    = s"http://localhost:20202/plastic-packaging-tax/export-credits/PPT/$pptReference?fromDate=$fromDate&toDate=$toDate"
            val errors = "'{\"failures\":[{\"code\":\"Error Code\",\"reason\":\"Error Reason\"}]}'"
            val error  = s"GET of '$url' returned $statusCode. Response body: $errors"

            val auditModel = GetExportCredits(internalId, pptReference, fromDate, toDate, "Failure", None, Some(error))

            stubExportCreditBalanceDisplayFailure(httpStatus = statusCode,
              errors = Seq(EISError("Error Code", "Error Reason"))
            )

            givenAuditReturns(auditUrl, Status.NO_CONTENT)
            givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

            val res = await(connector.getBalance(pptReference, fromDate, toDate, internalId))

            res.left.get mustBe statusCode

            eventually(timeout(Span(5, Seconds))) {
              eventSendToAudit(auditUrl, auditModel) mustBe true
            }

            getTimer(displayExportCreditBalanceTimer).getCount mustBe 1

          }
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

  private def givenAuditReturns(url: String, statusCode: Int): Unit =
    stubFor(
      post(url)
        .willReturn(
          aResponse()
            .withStatus(statusCode)
        )
    )

  private def eventSendToAudit(url: String, displayResponse: GetExportCredits): Boolean =
    eventSendToAudit(url, GetExportCredits.eventType, GetExportCredits.format.writes(displayResponse).toString())

}
