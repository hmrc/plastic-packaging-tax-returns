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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError

import java.time.LocalDate

class ObligationsDataConnectorISpec extends ConnectorISpec with Injector with ScalaFutures with EitherValues {

  lazy val connector: ObligationsDataConnector = app.injector.instanceOf[ObligationsDataConnector]

  val getObligationDataTimer = "ppt.get.obligation.data.timer"

  val internalId: String               = "someId"
  val pptReference: String             = "XXPPTP103844123"
  val fromDate: Option[LocalDate]      = Some(LocalDate.parse("2021-10-01"))
  val toDate: Option[LocalDate]        = Some(LocalDate.parse("2021-10-31"))
  val status: Option[ObligationStatus] = Some(ObligationStatus.OPEN)
  val auditUrl: String                 = "/write/audit"
  val implicitAuditUrl: String         = s"$auditUrl/merged"

  val url =
    s"/enterprise/obligation-data/zppt/$pptReference/PPT?from=${fromDate.get}&to=${toDate.get}&status=${status.get}"

  val response: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Some(Identification(
            incomeSourceType = Some("ITR SA"),
            referenceNumber = pptReference,
            referenceType = "PPT"
          )),
        obligationDetails = Seq(
          ObligationDetail(
            status = ObligationStatus.OPEN,
            inboundCorrespondenceFromDate = LocalDate.parse("2021-10-01"),
            inboundCorrespondenceToDate = LocalDate.parse("2021-11-01"),
            inboundCorrespondenceDateReceived = Some(LocalDate.parse("2021-10-01")),
            inboundCorrespondenceDueDate = LocalDate.parse("2021-10-31"),
            periodKey = "#001"
          )
        )
      )
    )
  )

  override def overrideConfig: Map[String, Any] =
    Map(
      "microservice.services.des.host" -> wireHost,
      "microservice.services.des.port" -> wirePort,
      "auditing.consumer.baseUri.port" -> wirePort,
      "auditing.enabled"               -> true
    )

  "ObligationData connector" when {

    "get obligation data" should {

      "handle a 200 with obligation data" in {

        val auditModel =
          GetObligations(ObligationStatus.OPEN.toString, internalId, pptReference, "Success", Some(response), None)

        stubObligationDataRequest(response)

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(connector.get(pptReference, internalId, fromDate, toDate, status))
        res.value mustBe response

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(getObligationDataTimer).getCount mustBe 1

      }

      "return a 200 when obligation data not found" in {

        val auditModel = GetObligations(
          ObligationStatus.OPEN.toString,
          internalId,
          pptReference,
          "Success",
          Some(ObligationDataResponse.empty),
          None
        )

        stubFor(
          get(url)
            .willReturn(
              WireMock
                .status(NOT_FOUND)
                .withBody("""{"code": "NOT_FOUND","message": "any message"}""")
            )
        )

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val res = await(connector.get(pptReference, internalId, fromDate, toDate, status))
        res.value mustBe ObligationDataResponse.empty

        eventually(timeout(Span(5, Seconds))) {
          eventSendToAudit(auditUrl, auditModel) mustBe true
        }

        getTimer(getObligationDataTimer).getCount mustBe 1

      }

      "handle bad json" in {

        stubFor(
          get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        givenAuditReturns(auditUrl, Status.NO_CONTENT)
        givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

        val result = await(connector.get(pptReference, internalId, fromDate, toDate, status))
        result mustBe Left(INTERNAL_SERVER_ERROR)

        getTimer(getObligationDataTimer).getCount mustBe 1

      }

    }
  }

  "ObligationData connector for obligation data" should {

    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {

        s"$statusCode is returned from downstream service" in {

          stubObligationDataRequestFailure(
            httpStatus = statusCode,
            errors = Seq(EISError("Error Code", "Error Reason"))
          )

          givenAuditReturns(auditUrl, Status.NO_CONTENT)
          givenAuditReturns(implicitAuditUrl, Status.NO_CONTENT)

          val res = await(connector.get(pptReference, internalId, fromDate, toDate, status))

          res.left.value mustBe statusCode

          getTimer(getObligationDataTimer).getCount mustBe 1

        }
      }

    }
  }

  private def stubObligationDataRequest(response: ObligationDataResponse): Unit = {
    implicit val odWrites: OWrites[ObligationDetail] = Json.writes[ObligationDetail]
    implicit val oWrites: OWrites[Obligation]        = Json.writes[Obligation]
    val writes: OWrites[ObligationDataResponse]      = Json.writes[ObligationDataResponse]
    stubFor(
      get(
        s"/enterprise/obligation-data/zppt/$pptReference/PPT?from=${fromDate.get}&to=${toDate.get}&status=${status.get}"
      )
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(response)(writes).toString())
        )
    )
  }

  private def stubObligationDataRequestFailure(httpStatus: Int, errors: Seq[EISError]): Any =
    stubFor(
      get(url)
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

  private def eventSendToAudit(url: String, displayResponse: GetObligations): Boolean =
    eventSendToAudit(url, GetObligations.eventType, GetObligations.format.writes(displayResponse).toString())

}
