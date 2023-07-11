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

package returns

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType

import java.time.LocalDate

class ReturnsConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  private val returnsConnector = app.injector.instanceOf[ReturnsConnector]

  private val internalId: String   = "someId"
  private val pptReference: String = "XMPPT0000000123"
  val auditUrl: String             = "/write/audit"
  val implicitAuditUrl: String     = s"$auditUrl/merged"
  val periodKey: String            = "22C1"

  val getUrl = s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"
  val putUrl = s"http://localhost:20202/plastic-packaging-tax/returns/PPT/$pptReference"

  override def overrideConfig: Map[String, Any] =
    Map("microservice.services.eis.host" -> wireHost,
      "microservice.services.eis.port" -> wirePort,
      "auditing.consumer.baseUri.port" -> wirePort,
      "auditing.enabled" -> true
    )

  "Returns Connector" when {

    "submitting a return" should {

      "return expected response" in {

        val returnsSubmissionResponse = aReturn()

        val auditModel = SubmitReturn(internalId, pptReference, "Success", aReturnsSubmissionRequest, Some(returnsSubmissionResponse), None)

        stubSuccessfulReturnsSubmission(pptReference, returnsSubmissionResponse)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

        res mustBe Right(returnsSubmissionResponse)

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

      }

      "handle bad json" in {

        // TODO discuss what to send to secure log
//        val error = "Unrecognized token 'XXX': was expecting (JSON String, Number, Array, Object or token 'null', " +
//          "'true' or 'false')\n at [Source: (String)\"XXX\"; line: 1, column: 4]"
        val error = "${json-unit.any-string}"
        
        val auditModel = SubmitReturn(internalId, pptReference, "Failure", aReturnsSubmissionRequest, None, Some(error))

        stubFailedReturnsSubmission(pptReference, Status.OK, "XXX")

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

        res.leftSideValue mustBe Left(Status.INTERNAL_SERVER_ERROR)

        verifyAuditRequest(auditUrl, SubmitReturn.eventType, SubmitReturn.format.writes(auditModel).toString())

      }

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>

        s"return $statusCode" when {

          s"upstream service fails with $statusCode" in {

            val errors = "{\"failures\":[{\"code\":\"Error Code\",\"reason\":\"Error Reason\"}]}"
            
            // TODO I think this bit is wrong? Just log the error response?
//            val error   = s"PUT of '$putUrl' returned $statusCode. Response body: '$errors'"

            val auditModel = SubmitReturn(internalId, pptReference, "Failure", aReturnsSubmissionRequest(), None, Some(errors))

            stubFailedReturnsSubmission(pptReference, statusCode, errors =
              Seq(EISError("Error Code", "Error Reason"))
            )

            givenAuditReturns(auditUrl, Status.NO_CONTENT)
            givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

            val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

            res.leftSideValue mustBe Left(statusCode)

            // TODO this fails sometimes even when it shouldn't?
            verifyAuditRequest(auditUrl, SubmitReturn.eventType, Json.toJson(auditModel).toString())
            
            // TODO this (below) eats any / all output from WireMock's matchers
            eventually(timeout(Span(5, Seconds))) {
              eventSendToAudit(auditUrl, auditModel) mustBe true
            }

          }
        }
      }
    }

    "get return " should {

      "return expected response" in {

        val returnForDisplay: Return = aReturnWithReturnDetails()

        val auditModel = GetReturn(internalId, periodKey, "Success", Some(Json.toJson(returnForDisplay)), None)

        stubSuccessfulReturnDisplay(pptReference, periodKey, returnForDisplay)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.get(pptReference, periodKey, internalId))

        res mustBe Right(Json.toJson(returnForDisplay))

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

      }

      "handle bad json" in {

        val error = "Response body could not be read as type play.api.libs.json.JsValue"
        val auditModel = GetReturn(internalId, periodKey, "Failure", None, Some(error))

        stubFailedReturnDisplay(pptReference, periodKey, Status.OK, "XXX")
        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.get(pptReference, periodKey, internalId))

        res.leftSideValue mustBe Left(Status.INTERNAL_SERVER_ERROR)

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

      }

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>

        s"return $statusCode" when {

          s"upstream service fails with $statusCode" in {

            val error   = "errors = Seq(EISError(\"Error Code\", \"Error Reason\"))"

            val auditModel = GetReturn(internalId, periodKey, "Failure", None, Some(error))

            stubFailedReturnDisplay(pptReference, periodKey, statusCode,
              "errors = Seq(EISError(\"Error Code\", \"Error Reason\"))"
            )

            givenAuditReturns(auditUrl, Status.NO_CONTENT)
            givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

            val res = await(returnsConnector.get(pptReference, periodKey, internalId))

            res.leftSideValue mustBe Left(statusCode)

            eventually(timeout(Span(5, Seconds))) {
              eventSendToAudit(auditUrl, auditModel) mustBe true
            }

          }
        }
      }
    }
  }

  private def aReturn() =
    Return(processingDate = LocalDate.now().toString,
           idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
           chargeDetails = Some(
             ChargeDetails(chargeType = "Plastic Tax",
                           chargeReference = "ABC123",
                           amount = 1234.56,
                           dueDate = LocalDate.now().plusDays(30).toString
             )
           ),
           exportChargeDetails = None,
           returnDetails = None
    )

  private def aReturnWithReturnDetails() =
    Return(processingDate = LocalDate.now().toString,
           idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
           chargeDetails = Some(
             ChargeDetails(chargeType = "Plastic Tax",
                           chargeReference = "ABC123",
                           amount = 1234.56,
                           dueDate = LocalDate.now().plusDays(30).toString
             )
           ),
           exportChargeDetails = None,
           returnDetails = Some(
             EisReturnDetails(manufacturedWeight = BigDecimal(256.12),
                              importedWeight = BigDecimal(352.15),
                              totalNotLiable = BigDecimal(546.42),
                              humanMedicines = BigDecimal(1234.15),
                              directExports = BigDecimal(12121.16),
                              recycledPlastic = BigDecimal(4345.72),
                              creditForPeriod =
                                BigDecimal(1560000.12),
                              totalWeight = BigDecimal(16466.88),
                              taxDue = BigDecimal(4600)
             )
           )
    )

  private def stubSuccessfulReturnsSubmission(returnId: String, resp: Return) =
    stubFor(
      put(urlMatching(s"/plastic-packaging-tax/returns/PPT/$returnId"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(resp).toString)
        )
    )

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, errors: String) =
    stubFor(
      put(urlMatching(s"/plastic-packaging-tax/returns/PPT/$returnId"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(errors)
        )
    )

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, errors: Seq[EISError]) =
    stubFor(
      put(urlMatching(s"/plastic-packaging-tax/returns/PPT/$returnId"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(Json.obj("failures" -> errors).toString)
        )
    )

  private def aReturnsSubmissionRequest() =
    ReturnsSubmissionRequest(returnType = ReturnType.NEW,
                             submissionId = None,
                             periodKey = "AA22",
                             returnDetails = EisReturnDetails(manufacturedWeight = 12000,
                                                              importedWeight = 1000,
                                                              totalNotLiable = 2000,
                                                              humanMedicines = 3000,
                                                              directExports = 4000,
                                                              recycledPlastic = 5000,
                                                              creditForPeriod = 10000,
                                                              totalWeight = 20000,
                                                              taxDue = 90000
                             )
    )

  private def stubSuccessfulReturnDisplay(pptReference: String, periodKey: String, response: Return) =
    stubFor(
      get(urlMatching(s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(response).toString)
        )
    )

  private def stubFailedReturnDisplay(pptReference: String, periodKey: String, statusCode: Int, body: String) =
    stubFor(
      get(urlMatching(s"/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(body)
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

  private def eventSendToAudit(url: String, displayResponse: GetReturn): Boolean =
    eventSendToAudit(url, GetReturn.eventType, GetReturn.format.writes(displayResponse).toString())

  private def eventSendToAudit(url: String, displayResponse: SubmitReturn): Boolean =
    eventSendToAudit(url, SubmitReturn.eventType, SubmitReturn.format.writes(displayResponse).toString())

}
