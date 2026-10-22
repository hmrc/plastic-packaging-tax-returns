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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.subscriptions

import play.api.http.Status
import play.api.http.Status.{NOT_FOUND, OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.HipConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.hip.{Hip422Error, HipInner422Err}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class HipSubscriptionsConnector @Inject() (
  httpClient: HttpClientV2,
  override val appConfig: AppConfig,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends SubscriptionsConnector with HipConnector {

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateSuccessfulResponse] = {
    val correlationId: String = UUID.randomUUID().toString
    val timer                 = metrics.defaultRegistry.timer("ppt.subscription.update.timer").time()

    httpClient
      .put(appConfig.hipSubscriptionsUrl(pptReference))
      .withBody(Json.toJson(subscriptionUpdateDetails))
      .setHeader(hipHeaders(correlationId)*)
      .execute[HttpResponse](readRaw, ec)
      .andThen { case _ => timer.stop() }
      .map { response =>
        response.status match {
          case Status.OK =>
            Try((Json.parse(response.body) \ "success").as[SubscriptionUpdateSuccessfulResponse]).match {
              case Success(subscription) => subscription
              case Failure(exception)    => throwException(pptReference, correlationId, exception)
            }
          case _ =>
            throwException(pptReference, correlationId, response.status)
        }
      }
  }

  def getSubscription(pptReference: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[EisHttpResponse, SubscriptionDisplayResponse]] = {
    val correlationId: String = UUID.randomUUID().toString
    val timer                 = metrics.defaultRegistry.timer("ppt.subscription.display.timer").time()

    httpClient
      .get(appConfig.hipSubscriptionsUrl(pptReference))
      .setHeader(hipHeaders(correlationId)*)
      .execute[HttpResponse]
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"PPT view subscription with correlationId [${correlationId}] and pptReference [$pptReference]"
        )

        response.status match {
          case OK =>
            val sanitisedJson =
              Json.parse(response.body.replaceAll("\\s", " "))
            Right(
              (sanitisedJson \ "success").as[SubscriptionDisplayResponse]
            )
          case UNPROCESSABLE_ENTITY =>
            response.json.as[Hip422Error].error match {
              case HipInner422Err("002", _, _) =>
                // Converting new error representing NOT_FOUND to the EIS
                val body =
                  """
                    |{
                    |  "failures": [
                    |    {
                    |      "code": "NO_DATA_FOUND",
                    |      "reason": "The remote endpoint has indicated that the requested resource could not be found.                        "
                    |    }
                    |  ]
                    |}
                    |""".stripMargin
                Left(EisHttpResponse(NOT_FOUND, body, correlationId))
              case _ => Left(EisHttpResponse.fromHttpResponse(correlationId)(response))
            }
          case _ => Left(EisHttpResponse.fromHttpResponse(correlationId)(response))
        }
      }
  }

  def getSubscriptionFuture(pptReference: String)(implicit hc: HeaderCarrier): Future[SubscriptionDisplayResponse] =
    getSubscription(pptReference).flatMap {
      case Right(subscription) => Future.successful(subscription)
      case Left(errorResponse) =>
        Future.failed(
          new RuntimeException(
            "Failed to fetch subscription details from api, " +
              s"response code ${errorResponse.status}" +
              s"and body ${errorResponse.body}"
          )
        )
    }

}
