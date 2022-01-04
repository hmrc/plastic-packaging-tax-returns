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

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{EISError, EnterpriseTestData, SubscriptionTestData}

class FinancialDataConnectorISpec
    extends ConnectorISpec with Injector with SubscriptionTestData with ScalaFutures with EnterpriseTestData {

  lazy val connector: FinancialDataConnector = app.injector.instanceOf[FinancialDataConnector]

  val getFinancialDataTimer = "ppt.get.financial.data.timer"

  val pptReference                                = "XXPPTP103844123"
  val fromDate: LocalDate                         = LocalDate.parse("2021-10-01")
  val toDate: LocalDate                           = LocalDate.parse("2021-10-31")
  val onlyOpenItems: Option[Boolean]              = Some(true)
  val includeLocks: Option[Boolean]               = Some(false)
  val calculateAccruedInterest: Option[Boolean]   = Some(true)
  val customerPaymentInformation: Option[Boolean] = Some(false)

  val getUrl =
    s"/enterprise/financial-data/ZPPT/$pptReference/PPT?dateFrom=$fromDate&dateTo=$toDate&onlyOpenItems=${onlyOpenItems.get}&includeLocks=${includeLocks.get}&calculateAccruedInterest=${calculateAccruedInterest.get}&customerPaymentInformation=${customerPaymentInformation.get}"

  "FinancialData connector" when {
    "get financial data" should {
      "handle a 200 with financial data" in {

        stubFinancialDataRequest(financialDataResponse)

        val res = await(getFinancialData)
        res.right.get mustBe financialDataResponse

        getTimer(getFinancialDataTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        stubFor(
          get(getUrl)
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        val res = await(getFinancialData)

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
        getTimer(getFinancialDataTimer).getCount mustBe 1
      }

    }
  }

  private def getFinancialData =
    connector.get(pptReference,
                  fromDate,
                  toDate,
                  onlyOpenItems,
                  includeLocks,
                  calculateAccruedInterest,
                  customerPaymentInformation
    )

  "FinancialData connector for obligation data" should {
    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {
        statusCode + " is returned from downstream service" in {
          stubFinancialDataRequestFailure(httpStatus = statusCode, errors = Seq(EISError("Error Code", "Error Reason")))

          val res = await(getFinancialData)

          res.left.get mustBe statusCode
          getTimer(getFinancialDataTimer).getCount mustBe 1
        }
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

}
