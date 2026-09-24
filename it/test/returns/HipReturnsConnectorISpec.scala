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

package returns

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.EitherValues
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status
import play.api.libs.json.{Json, OFormat}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.HipReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.*
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType

import java.time.LocalDate

class HipReturnsConnectorISpec extends ConnectorISpec with Injector with ScalaFutures with EitherValues {

  private val returnsConnector = app.injector.instanceOf[HipReturnsConnector]

  private val internalId: String   = "someId"
  private val pptReference: String = "XMPPT0000000123"
  val auditUrl: String             = "/write/audit"
  val implicitAuditUrl: String     = s"$auditUrl/merged"
  val periodKey: String            = "22C1"
  val putPath                      = "/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT/"

  override def overrideConfig: Map[String, Any] =
    Map(
      "microservice.services.hip.host"     -> wireHost,
      "microservice.services.hip.port"     -> wirePort,
      "microservice.services.hip.clientId" -> "foo",
      "microservice.services.hip.secret"   -> "bar",
      "auditing.consumer.baseUri.port"     -> wirePort,
      "auditing.enabled"                   -> true
    )

  "Returns Connector" when {

    "submitting a return" should {

      "return expected response" in {

        val returnsSubmissionResponse = aHipSuccessReturn

        val auditModel = SubmitReturn(
          internalId,
          pptReference,
          "Success",
          aReturnsSubmissionRequest(),
          Some(returnsSubmissionResponse.success),
          None
        )

        stubSuccessfulReturnsSubmission(pptReference, returnsSubmissionResponse)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

        res mustBe Right(returnsSubmissionResponse.success)

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

      }

      "handle bad json" in {

        val error = s"$${json-unit.any-string}"

        val auditModel =
          SubmitReturn(internalId, pptReference, "Failure", aReturnsSubmissionRequest(), None, Some(error))

        stubFailedReturnsSubmission(pptReference, Status.OK, """{"foo":"bar"}""")

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

        res.left.value mustBe Status.INTERNAL_SERVER_ERROR

        verifyAuditRequest(auditUrl, SubmitReturn.eventType, SubmitReturn.format.writes(auditModel).toString())

      }

      "handle 422 044 correctly" in {

        val error = s"$${json-unit.any-string}"
        val errorResponse =
          """
            |{
            |  "error": {
            |    "errorId": "044",
            |    "processingDate": "2026-07-09T09:26:17Z",
            |    "text": "Tax Obligation Already Fulfilled"
            |  }
            |}
            |""".stripMargin

        val auditModel =
          SubmitReturn(internalId, pptReference, "Failure", aReturnsSubmissionRequest(), None, Some(error))

        stubFailedReturnsSubmission(pptReference, 208, errorResponse)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

        res.left.value mustBe 208

        verifyAuditRequest(auditUrl, SubmitReturn.eventType, SubmitReturn.format.writes(auditModel).toString())
      }

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
        s"return $statusCode" when {

          s"upstream service fails with $statusCode" in {

            val errors = "{\"failures\":[{\"code\":\"Error Code\",\"reason\":\"Error Reason\"}]}"

            val auditModel =
              SubmitReturn(internalId, pptReference, "Failure", aReturnsSubmissionRequest(), None, Some(errors))

            stubFailedReturnsSubmission(
              pptReference,
              statusCode,
              errors =
                Seq(EISError("Error Code", "Error Reason"))
            )

            givenAuditReturns(auditUrl, Status.NO_CONTENT)
            givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

            val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest(), internalId))

            res.left.value mustBe statusCode

            verifyAuditRequest(auditUrl, SubmitReturn.eventType, Json.toJson(auditModel).toString())

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

        val auditModel =
          GetReturn(internalId, periodKey, "Success", Some(Json.obj("success" -> Json.toJson(returnForDisplay))), None)

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

        val error      = s"$${json-unit.any-string}"
        val auditModel = GetReturn(internalId, periodKey, "Failure", None, Some(error))

        stubFailedReturnDisplay(pptReference, periodKey, Status.OK, "XXXX")
        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(returnsConnector.get(pptReference, periodKey, internalId))
        println(res)
        res.left.value mustBe Status.INTERNAL_SERVER_ERROR

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

      }

      forAll(Seq(400, 401, 403, 404, 500, 503)) { statusCode =>
        s"return $statusCode" when {

          s"upstream service fails with $statusCode" in {

            val error = s"$${json-unit.any-string}"

            val errorResponse = if (Seq(400, 500, 503).contains(statusCode))
              """
                |{
                |  "origin": "HIP",
                |  "response": {
                |    "failures": [
                |      {
                |        "type": "Type of Failure",
                |        "reason": "Reason for Failure"
                |      }
                |    ]
                |  }
                |}""".stripMargin
            else ""

            val auditModel = GetReturn(internalId, periodKey, "Failure", None, Some(error))

            stubFailedReturnDisplay(
              pptReference,
              periodKey,
              statusCode,
              errorResponse
            )

            givenAuditReturns(auditUrl, Status.NO_CONTENT)
            givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

            val res = await(returnsConnector.get(pptReference, periodKey, internalId))

            res.left.value mustBe statusCode

            eventually(timeout(Span(5, Seconds))) {
              eventSendToAudit(auditUrl, auditModel) mustBe true
            }

          }
        }
      }
    }
  }

  case class HipSuccessReturnResponse(success: Return)

  case object HipSuccessReturnResponse {
    implicit val formats: OFormat[HipSuccessReturnResponse] = Json.format[HipSuccessReturnResponse]
  }

  def aHipSuccessReturn = HipSuccessReturnResponse(aReturn())

  private def aReturn(): Return =
    Return(
      processingDate = LocalDate.now().toString,
      idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
      chargeDetails = Some(
        ChargeDetails(
          chargeType = "Plastic Tax",
          chargeReference = "ABC123",
          amount = 1234.56,
          dueDate = LocalDate.now().plusDays(30).toString
        )
      ),
      exportChargeDetails = None,
      returnDetails = None
    )

  private def stubSuccessfulReturnsSubmission(returnId: String, resp: HipSuccessReturnResponse) =
    stubFor(
      put(urlMatching(putPath + returnId))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(resp).toString)
        )
    )

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, errors: String) =
    stubFor(
      put(urlMatching(putPath + returnId))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(errors)
        )
    )

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, errors: Seq[EISError]) =
    stubFor(
      put(urlMatching(putPath + returnId))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(Json.obj("failures" -> errors).toString)
        )
    )

  private def aReturnsSubmissionRequest() =
    ReturnsSubmissionRequest(
      returnType = ReturnType.NEW,
      submissionId = None,
      periodKey = "AA22",
      returnDetails = EisReturnDetails(
        manufacturedWeight = 12000,
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

  private def aReturnWithReturnDetails() =
    Return(
      processingDate = LocalDate.now().toString,
      idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
      chargeDetails = Some(
        ChargeDetails(
          chargeType = "Plastic Tax",
          chargeReference = "ABC123",
          amount = 1234.56,
          dueDate = LocalDate.now().plusDays(30).toString
        )
      ),
      exportChargeDetails = None,
      returnDetails = Some(
        EisReturnDetails(
          manufacturedWeight = BigDecimal(256.12),
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

  private def stubSuccessfulReturnDisplay(pptReference: String, periodKey: String, response: Return) =
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.obj("success" -> Json.toJson(response)).toString)
        )
    )

  private def stubFailedReturnDisplay(pptReference: String, periodKey: String, statusCode: Int, body: String) =
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(Json.toJson(body).toString)
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

  private def eventSendToAudit(url: String, displayResponse: SubmitReturn): Boolean =
    eventSendToAudit(url, SubmitReturn.eventType, SubmitReturn.format.writes(displayResponse).toString())

  private def eventSendToAudit(url: String, displayResponse: GetReturn): Boolean =
    eventSendToAudit(url, GetReturn.eventType, GetReturn.format.writes(displayResponse).toString())

}
