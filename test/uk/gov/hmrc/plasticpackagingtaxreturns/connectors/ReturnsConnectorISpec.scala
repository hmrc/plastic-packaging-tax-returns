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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, put, urlMatching}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import java.time.LocalDate

class ReturnsConnectorISpec extends ConnectorISpec with Injector with ScalaFutures {

  private val returnsConnector = app.injector.instanceOf[ReturnsConnector]

  private val pptReference = "XMPPT0000000123"

  "Returns Connector" when {
    "submitting a return" should {
      "return expected response" in {
        val returnsSubmissionResponse = aReturn()
        stubSuccessfulReturnsSubmission(pptReference, returnsSubmissionResponse)

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest()))

        res.right.get mustBe returnsSubmissionResponse
      }

      "return error when unexpected response received" in {
        stubFailedReturnsSubmission(pptReference, Status.OK, "XXX")

        val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest()))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
      }

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
        s"return $statusCode" when {
          s"upstream service fails with $statusCode" in {
            stubFailedReturnsSubmission(pptReference, statusCode, "")

            val res = await(returnsConnector.submitReturn(pptReference, aReturnsSubmissionRequest()))

            res.left.get mustBe statusCode
          }
        }
      }
    }

    "get return " should {
      "return expected response" in {
        val returnForDisplay = aReturnWithReturnDetails()
        stubSuccessfulReturnDisplay(pptReference, "22C1", returnForDisplay)

        val res = await(returnsConnector.get(pptReference, "22C1"))

        res.right.get mustBe returnForDisplay
      }

      "return error when unexpected response received" in {
        stubFailedReturnDisplay(pptReference, "22C2", Status.OK, "XXX")

        val res = await(returnsConnector.get(pptReference, "22C2"))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
      }

      forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
        s"return $statusCode" when {
          s"upstream service fails with $statusCode" in {
            stubFailedReturnDisplay(pptReference, "22C2", statusCode, "")

            val res = await(returnsConnector.get(pptReference, "22C2"))

            res.left.get mustBe statusCode
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

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, body: String) =
    stubFor(
      put(urlMatching(s"/plastic-packaging-tax/returns/PPT/$returnId"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(body)
        )
    )

  private def aReturnsSubmissionRequest() =
    ReturnsSubmissionRequest(returnType = "New",
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

}
