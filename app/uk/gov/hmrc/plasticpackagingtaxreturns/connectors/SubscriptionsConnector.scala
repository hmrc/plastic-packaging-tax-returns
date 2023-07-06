/*
 * Copyright 2023 HM Revenue & Customs
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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EisHttpClient, EisHttpResponse}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class SubscriptionsConnector @Inject()
(
  eisHttpClient: EisHttpClient,
  httpClient: HttpClient,
  override val appConfig: AppConfig,
  metrics: Metrics
)(
  implicit ec: ExecutionContext
) extends EISConnector {

  private val logger = Logger(this.getClass)

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateSuccessfulResponse] = {
    val timer: Timer.Context                  = metrics.defaultRegistry.timer("ppt.subscription.update.timer").time()
    val correlationIdHeader: (String, String) = correlationIdHeaderName -> UUID.randomUUID().toString
    httpClient.PUT[SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse](
      url = appConfig.subscriptionUpdateUrl(pptReference),
      body = subscriptionUpdateDetails,
      headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .andThen {
        case Success(response) =>
          logger.info(
            s"PPT subscription update sent with correlationId [${correlationIdHeader._2}] and pptReference [$pptReference] had response payload ${toJson(response)}"
          )
          response
        case Failure(exception) =>
          throw new Exception(
            s"Subscription update with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference] is currently unavailable due to [${exception.getMessage}]",
            exception
          )
      }
  }

  def getSubscription(pptReference: String)(implicit hc: HeaderCarrier): Future[Either[EisHttpResponse, SubscriptionDisplayResponse]] = {

    val timerName =  "ppt.subscription.display.timer"

    val url = appConfig.subscriptionDisplayUrl(pptReference)
    eisHttpClient.get(url, Seq.empty, timerName, buildEisHeader)
      .map { response =>
        logger.info(s"PPT view subscription with correlationId [${response.correlationId}] and pptReference [$pptReference]")
        if (Status.isSuccessful(response.status)) {
          val json = Json.parse(response.body.replaceAll("\\s", " "))//subscription data can come back un sanitised for json.
          Right(json.as[SubscriptionDisplayResponse])
        } else {
          Left(response)
        }
      }
  }

  def getSubscriptionFuture(pptReference: String)(implicit hc: HeaderCarrier): Future[SubscriptionDisplayResponse] = {
    getSubscription(pptReference).flatMap {
      case Right(subscription) => Future.successful(subscription)
      case Left(errorResponse) => Future.failed(new RuntimeException("Failed to fetch subscription details from api, " +
        s"response code ${errorResponse.status}"))
    }
  }
}
