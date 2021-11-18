/*
 * Copyright 2021 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, put}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{
  ChargeDetails,
  EisReturnDetails,
  IdDetails,
  ReturnsSubmissionRequest,
  ReturnsSubmissionResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import java.time.LocalDate

class ReturnsConnectorISpec extends ConnectorISpec with Injector with SubscriptionTestData with ScalaFutures {

  private val returnsConnector = app.injector.instanceOf[ReturnsConnector]

  private val pptReference = "XMPPT0000000123"

  "Returns Connector" when {
    "submitting a return" should {
      "return expected response" in {
        val returnsSubmissionResponse = aReturnsSubmissionResponse()
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
  }

  private def aReturnsSubmissionResponse() =
    ReturnsSubmissionResponse(processingDate = LocalDate.now().toString,
                              idDetails = IdDetails(pptReferenceNumber = pptReference, submissionId = "1234567890XX"),
                              chargeDetails = Some(
                                ChargeDetails(chargeType = "Plastic Tax",
                                              chargeReference = "ABC123",
                                              amount = 1234.56,
                                              dueDate = LocalDate.now().plusDays(30).toString
                                )
                              ),
                              exportChargeDetails = None
    )

  private def stubSuccessfulReturnsSubmission(returnId: String, resp: ReturnsSubmissionResponse) =
    stubFor(
      put(s"/plastic-packaging-tax/returns/PPT/$returnId")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(Json.toJson(resp).toString)
        )
    )

  private def stubFailedReturnsSubmission(returnId: String, statusCode: Int, body: String) =
    stubFor(
      put(s"/plastic-packaging-tax/returns/PPT/$returnId")
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

}
