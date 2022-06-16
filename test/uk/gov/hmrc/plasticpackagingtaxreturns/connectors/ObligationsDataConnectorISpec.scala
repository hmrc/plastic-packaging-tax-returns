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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, get}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}

import java.time.LocalDate

class ObligationsDataConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  lazy val connector: ObligationsDataConnector = app.injector.instanceOf[ObligationsDataConnector]

  val getObligationDataTimer = "ppt.get.obligation.data.timer"

  val pptReference                     = "XXPPTP103844123"
  val fromDate: Option[LocalDate]      = Some(LocalDate.parse("2021-10-01"))
  val toDate: Option[LocalDate]        = Some(LocalDate.parse("2021-10-31"))
  val status: Option[ObligationStatus] = Some(ObligationStatus.OPEN)

  val response: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Some(Identification(incomeSourceType = Some("ITR SA"), referenceNumber = pptReference, referenceType = "PPT")),
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

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    wiremock.resetAll()
  }

  "ObligationData connector" when {

    "get obligation data" should {
      "handle a 200 with obligation data" in {

        stubObligationDataRequest(response)

        val res = await(connector.get(pptReference, fromDate, toDate, status))
        res.right.get mustBe response

        getTimer(getObligationDataTimer).getCount mustBe 1
      }

      "return a 200 when obligation data not found" in {

        stubFor(
          get(anyUrl())
            .willReturn(
              WireMock
                .status(NOT_FOUND)
                .withBody(ObligationsDataConnector.EmptyDataMessage)
            )
        )

        val res = await(connector.get(pptReference, fromDate, toDate, status))
        res.right.get mustBe ObligationDataResponse.empty

        getTimer(getObligationDataTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        stubFor(
          get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        val res = await(connector.get(pptReference, fromDate, toDate, status))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
        getTimer(getObligationDataTimer).getCount mustBe 1
      }

    }
  }

  "ObligationData connector for obligation data" should {

    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {
        statusCode + " is returned from downstream service" in {

          stubFor(get(anyUrl()).willReturn(WireMock.status(statusCode)))

          val res = await(connector.get(pptReference, fromDate, toDate, status))

          res.left.get mustBe statusCode
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
      get(s"/enterprise/obligation-data/zppt/$pptReference/PPT?from=${fromDate.get}&to=${toDate.get}&status=${status.get}")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(response)(writes).toString())
        )
    )
  }

}
