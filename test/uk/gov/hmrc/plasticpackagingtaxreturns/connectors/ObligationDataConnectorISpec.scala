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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.EISError

import java.time.LocalDate

class ObligationDataConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  lazy val connector: ObligationDataConnector = app.injector.instanceOf[ObligationDataConnector]

  val getObligationDataTimer = "ppt.get.obligation.data.timer"

  val pptReference             = "XXPPTP103844123"
  val fromDate: LocalDate      = LocalDate.parse("2021-10-01")
  val toDate: LocalDate        = LocalDate.parse("2021-10-31")
  val status: ObligationStatus = ObligationStatus.OPEN

  val response: ObligationDataResponse = ObligationDataResponse(obligations =
    Seq(
      Obligation(
        identification =
          Identification(incomeSourceType = "ITR SA", referenceNumber = pptReference, referenceType = "PPT"),
        obligationDetails = Seq(
          ObligationDetail(status = ObligationStatus.OPEN,
                           inboundCorrespondenceFromDate = LocalDate.parse("2021-10-01"),
                           inboundCorrespondenceToDate = LocalDate.parse("2021-11-01"),
                           inboundCorrespondenceDateReceived = LocalDate.parse("2021-10-01"),
                           inboundCorrespondenceDueDate = LocalDate.parse("2021-10-31"),
                           periodKey = "#001"
          )
        )
      )
    )
  )

  "ObligationData connector" when {
    "get obligation data" should {
      "handle a 200 with obligation data" in {

        stubObligationDataRequest(response)

        val res = await(connector.get(pptReference, fromDate, toDate, status))
        res.right.get mustBe response

        getTimer(getObligationDataTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        stubFor(
          get(
            s"/enterprise/obligation-data/zppt/$pptReference/PPT?fromDate=$fromDate&toDate=$toDate&status=${status.toString}"
          )
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
          stubObligationDataRequestFailure(httpStatus = statusCode,
                                           errors = Seq(EISError("Error Code", "Error Reason"))
          )

          val res = await(connector.get(pptReference, fromDate, toDate, status))

          res.left.get mustBe statusCode
          getTimer(getObligationDataTimer).getCount mustBe 1
        }
      }
    }
  }

  private def stubObligationDataRequest(response: ObligationDataResponse): Unit =
    stubFor(
      get(
        s"/enterprise/obligation-data/zppt/$pptReference/PPT?fromDate=$fromDate&toDate=$toDate&status=${status.toString}"
      )
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(ObligationDataResponse.format.writes(response).toString())
        )
    )

  private def stubObligationDataRequestFailure(httpStatus: Int, errors: Seq[EISError]): Any =
    stubFor(
      get(
        s"/enterprise/obligation-data/zppt/$pptReference/PPT?fromDate=$fromDate&toDate=$toDate&status=${status.toString}"
      )
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
            .withBody(Json.obj("failures" -> errors).toString)
        )
    )

}
