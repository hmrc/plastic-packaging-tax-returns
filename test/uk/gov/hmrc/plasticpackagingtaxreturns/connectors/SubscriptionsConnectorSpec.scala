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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, put}
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.{Json, OFormat}
import play.api.test.Helpers.await
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.SubscriptionUpdateSuccessfulResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.{ConnectorISpec, Injector}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

class SubscriptionsConnectorSpec extends ConnectorISpec with Injector with SubscriptionTestData with ScalaFutures {

  lazy val connector: SubscriptionsConnector = app.injector.instanceOf[SubscriptionsConnector]

  val displaySubscriptionTimer = "ppt.subscription.display.timer"
  val updateSubscriptionTimer  = "ppt.subscription.update.timer"

  "Subscription connector" when {
    "requesting a subscription" should {
      "handle a 200 for uk company subscription" in {

        val pptReference = UUID.randomUUID().toString
        stubSubscriptionDisplay(pptReference, createSubscriptionDisplayResponse(ukLimitedCompanySubscription))

        await(connector.getSubscription(pptReference))

        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }

      "handle 200 for sole trader subscription" in {

        val pptReference = UUID.randomUUID().toString
        stubSubscriptionDisplay(pptReference, createSubscriptionDisplayResponse(soleTraderSubscription))

        await(connector.getSubscription(pptReference))

        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        val pptReference = UUID.randomUUID().toString

        stubFor(
          get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
                .withBody(Json.obj("rubbish" -> "errors").toString)
            )
        )

        val res = await(connector.getSubscription(pptReference))

        res.left.get mustBe Status.INTERNAL_SERVER_ERROR
        getTimer(displaySubscriptionTimer).getCount mustBe 1
      }
    }

    "requesting update subscription" should {
      "handle a 200 for updating uk company subscription" in {

        val pptReference               = UUID.randomUUID().toString
        val subscriptionProcessingDate = ZonedDateTime.now(ZoneOffset.UTC).toString
        val formBundleNumber           = "1234567890"

        stubSubscriptionUpdate(pptReference, subscriptionProcessingDate, formBundleNumber)

        val updateDetails =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription)
        val res: SubscriptionUpdateSuccessfulResponse =
          await(connector.updateSubscription(pptReference, updateDetails)).asInstanceOf[
            SubscriptionUpdateSuccessfulResponse
          ]

        res.pptReference mustBe pptReference
        res.formBundleNumber mustBe formBundleNumber
        res.processingDate mustBe ZonedDateTime.parse(subscriptionProcessingDate)
        getTimer(updateSubscriptionTimer).getCount mustBe 1
      }

      "handle 200 for sole trader subscription" in {

        val pptReference               = UUID.randomUUID().toString
        val subscriptionProcessingDate = ZonedDateTime.now(ZoneOffset.UTC).toString
        val formBundleNumber           = "1234567890"

        stubSubscriptionUpdate(pptReference, subscriptionProcessingDate, formBundleNumber)

        val updateDetails =
          createSubscriptionUpdateRequest(ukLimitedCompanySubscription)

        val res: SubscriptionUpdateSuccessfulResponse =
          await(connector.updateSubscription(pptReference, updateDetails)).asInstanceOf[
            SubscriptionUpdateSuccessfulResponse
          ]

        res.pptReference mustBe pptReference
        res.formBundleNumber mustBe formBundleNumber
        res.processingDate mustBe ZonedDateTime.parse(subscriptionProcessingDate)
        getTimer(updateSubscriptionTimer).getCount mustBe 1
      }

      "handle unexpected exceptions thrown" in {
        val pptReference = UUID.randomUUID().toString

        stubFor(
          put(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update")
            .willReturn(
              aResponse()
                .withStatus(Status.OK)
            )
        )

        intercept[Exception] {
          await(
            connector.updateSubscription(pptReference, createSubscriptionUpdateRequest(ukLimitedCompanySubscription))
          )
        }
      }
    }
  }

  "Subscription connector for display" should {
    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {
        statusCode + " is returned from downstream service" in {
          val pptReference = UUID.randomUUID().toString
          val errors =
            createErrorResponse(code = "INVALID_VALUE",
                                reason =
                                  "Some errors occurred"
            )

          stubSubscriptionDisplayFailure(httpStatus = statusCode, errors = errors, pptReference = pptReference)

          val res = await(connector.getSubscription(pptReference))

          res.left.get mustBe statusCode
          getTimer(displaySubscriptionTimer).getCount mustBe 1
        }
      }
    }
  }

  "Subscription connector for update" should {
    forAll(Seq(400, 404, 422, 409, 500, 502, 503)) { statusCode =>
      "return " + statusCode when {
        statusCode + " is returned from downstream service" in {
          val pptReference = UUID.randomUUID().toString

          stubSubscriptionUpdateFailure(httpStatus = statusCode, pptReference = pptReference)

          intercept[Exception] {
            await(
              connector.updateSubscription(pptReference, createSubscriptionUpdateRequest(ukLimitedCompanySubscription))
            )
          }
        }
      }
    }
  }

  private def createErrorResponse(code: String, reason: String): Seq[EISError] =
    Seq(EISError(code, reason))

  private def stubSubscriptionDisplayFailure(pptReference: String, httpStatus: Int, errors: Seq[EISError]): Any =
    stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
            .withBody(Json.obj("failures" -> errors).toString)
        )
    )

  private def stubSubscriptionDisplay(pptReference: String, response: SubscriptionDisplayResponse): Unit =
    stubFor(
      get(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/display")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(SubscriptionDisplayResponse.format.writes(response).toString())
        )
    )

  private def stubSubscriptionUpdate(
    pptReference: String,
    subscriptionProcessingDate: String,
    formBundleNumber: String
  ): Unit =
    stubFor(
      put(s"/plastic-packaging-tax/subscriptions/PPT/${pptReference}/update")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(
              Json.obj("pptReference"     -> pptReference,
                       "processingDate"   -> subscriptionProcessingDate,
                       "formBundleNumber" -> formBundleNumber
              ).toString
            )
        )
    )

  private def stubSubscriptionUpdateFailure(pptReference: String, httpStatus: Int): Any =
    stubFor(
      put(s"/plastic-packaging-tax/subscriptions/PPT/$pptReference/update")
        .willReturn(
          aResponse()
            .withStatus(httpStatus)
        )
    )

}

case class EISError(code: String, reason: String)

object EISError {
  implicit val format: OFormat[EISError] = Json.format[EISError]
}
