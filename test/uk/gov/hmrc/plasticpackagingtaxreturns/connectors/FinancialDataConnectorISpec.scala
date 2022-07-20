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
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetPaymentStatement
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{EISError, EnterpriseTestData}

import java.time.LocalDate

class FinancialDataConnectorISpec extends ConnectorISpec with Injector with ScalaFutures with EnterpriseTestData {

  lazy val connector: FinancialDataConnector = app.injector.instanceOf[FinancialDataConnector]

  val getFinancialDataTimer = "ppt.get.financial.data.timer"

  val internalId: String                          = "someId"
  val pptReference: String                        = "XXPPTP103844123"
  val fromDate: LocalDate                         = LocalDate.parse("2021-10-01")
  val toDate: LocalDate                           = LocalDate.parse("2021-10-31")
  val onlyOpenItems: Option[Boolean]              = Some(true)
  val includeLocks: Option[Boolean]               = Some(false)
  val calculateAccruedInterest: Option[Boolean]   = Some(true)
  val customerPaymentInformation: Option[Boolean] = Some(false)
  val auditUrl: String                            = "/write/audit"
  val implicitAuditUrl: String                    = s"$auditUrl/merged"

  val getUrl =
    s"/enterprise/financial-data/ZPPT/$pptReference/PPT?dateFrom=$fromDate&dateTo=$toDate&onlyOpenItems=${onlyOpenItems.get}&includeLocks=${includeLocks.get}&calculateAccruedInterest=${calculateAccruedInterest.get}&customerPaymentInformation=${customerPaymentInformation.get}"

  override def overrideConfig: Map[String, Any] =
    Map("microservice.services.des.host" -> wireHost,
      "microservice.services.des.port" -> wirePort,
      "auditing.consumer.baseUri.port" -> wirePort,
      "auditing.enabled" -> true
    )

  "FinancialData connector" when {

    "get financial data" should {

      "handle a 200 with financial data" in {

        val auditModel = GetPaymentStatement(internalId, pptReference, "Success", Some(financialDataResponse), None)

        stubFinancialDataRequest(financialDataResponse)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(getFinancialData)
        res.right.get mustBe financialDataResponse

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(getFinancialDataTimer).getCount mustBe 1
      }

      "handle bad json" in {

        val url = s"http://localhost:20202$getUrl"

        val error = s"GET of '$url' returned invalid json. Attempting to convert to " +
          s"uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse gave errors: " +
          s"List((/financialTransactions,List(JsonValidationError(List(error.path.missing),WrappedArray()))), " +
          s"(/processingDate,List(JsonValidationError(List(error.path.missing),WrappedArray()))))"

        val auditModel = GetPaymentStatement(internalId, pptReference, "Failure", None, Some(error))

        stubFor(
          get(getUrl)
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(getFinancialData)

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(getFinancialDataTimer).getCount mustBe 1

      }

    }
  }

  private def getFinancialData =
    connector.get(pptReference,
      Some(fromDate),
      Some(toDate),
      onlyOpenItems,
      includeLocks,
      calculateAccruedInterest,
      customerPaymentInformation,
      internalId
    )

  "FinancialData connector for obligation data" should {

    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>

      "return " + statusCode when {

        statusCode + " is returned from downstream service" in {

          val url = s"http://localhost:20202$getUrl"
          val errors = "'{\"failures\":[{\"code\":\"Error Code\",\"reason\":\"Error Reason\"}]}'"
          val error  = s"GET of '$url' returned $statusCode. Response body: $errors"

          val auditModel = GetPaymentStatement(internalId, pptReference, "Failure", None, Some(error))

          stubFinancialDataRequestFailure(httpStatus = statusCode, errors = Seq(EISError("Error Code", "Error Reason")))

          givenAuditReturns(auditUrl, Status.NO_CONTENT)
          givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

          val res = await(getFinancialData)

          res.left.get mustBe statusCode

          eventually(timeout(Span(5, Seconds))) {
            eventSendToAudit(auditUrl, auditModel) mustBe true
          }

          getTimer(getFinancialDataTimer).getCount mustBe 1

        }
      }
    }
    
    "it" should {
      "map special DES 404s to a zero financial records results" in {
        val desNotFound = Json.obj("code" -> "NOT_FOUND", "reason" -> "fish fryer fire") 
        wiremock.stubFor(get(anyUrl()).willReturn(notFound().withBody(desNotFound.toString)))
        val result = await(getFinancialData)
        result mustBe Right(FinancialDataResponse.inferNoTransactions)
      }
    }
  }

  private def stubFinancialDataRequest(response: FinancialDataResponse): Unit =
    stubFor(
      get(getUrl)
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(FinancialDataResponse.format.writes(response).toString())
        )
    )

  private def stubFinancialDataRequestFailure(httpStatus: Int, errors: Seq[EISError]): Any =
    stubFor(
      get(getUrl)
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

  private def eventSendToAudit(url: String, displayResponse: GetPaymentStatement): Boolean =
    eventSendToAudit(url, GetPaymentStatement.eventType, GetPaymentStatement.formats.writes(displayResponse).toString())

}
