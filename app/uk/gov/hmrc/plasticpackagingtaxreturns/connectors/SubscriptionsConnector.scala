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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateResponse, SubscriptionUpdateSuccessfulResponse}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class SubscriptionsConnector @Inject() (httpClient: HttpClient, override val appConfig: AppConfig, metrics: Metrics)(
  implicit ec: ExecutionContext
) extends EISConnector {

  private val logger = Logger(this.getClass)

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateResponse] = {
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

  def getSubscription(pptReference: String)(implicit hc: HeaderCarrier): Future[Either[HttpResponse, SubscriptionDisplayResponse]] = {

    val timer               = metrics.defaultRegistry.timer("ppt.subscription.display.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> UUID.randomUUID().toString

    val url = appConfig.subscriptionDisplayUrl(pptReference)
    httpClient.GET[HttpResponse](url, headers = headers :+ correlationIdHeader)
      .andThen { case _ => timer.stop() }
      .map { response =>
        if (Status.isSuccessful(response.status))
          Right(response.json.as[SubscriptionDisplayResponse])
        else {
          Left(response)
        }
      }
  }

}
