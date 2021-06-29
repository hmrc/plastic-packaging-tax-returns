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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{
  SubscriptionUpdateRequest,
  SubscriptionUpdateResponse
}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionsConnector @Inject() (httpClient: HttpClient, override val appConfig: AppConfig, metrics: Metrics)(
  implicit ec: ExecutionContext
) extends EISConnector {

  private val logger = Logger(this.getClass)

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, SubscriptionUpdateResponse]] = {
    val timer: Timer.Context                  = metrics.defaultRegistry.timer("ppt.subscription.update.timer").time()
    val correlationIdHeader: (String, String) = correlationId -> UUID.randomUUID().toString
    httpClient.PUT[SubscriptionUpdateRequest, SubscriptionUpdateResponse](
      url = appConfig.subscriptionUpdateUrl(pptReference),
      body = subscriptionUpdateDetails,
      headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .map(response => Right(response))
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.error(
            s"Upstream error returned on updating subscription, status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.error(s"Subscription update with Correlation ID [${correlationIdHeader._2}] and " +
                         s"PptReference [$pptReference] is currently unavailable due to [${ex.getMessage}]",
                       ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

  def getSubscription(
    pptReference: String
  )(implicit hc: HeaderCarrier): Future[Either[Int, SubscriptionDisplayResponse]] = {
    val timer               = metrics.defaultRegistry.timer("ppt.subscription.display.timer").time()
    val correlationIdHeader = correlationId -> UUID.randomUUID().toString
    httpClient.GET[SubscriptionDisplayResponse](appConfig.subscriptionDisplayUrl(pptReference),
                                                headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .map(response => Right(response))
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.error(
            s"Upstream error returned on viewing subscription, status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.error(s"Subscription display with Correlation ID [${correlationIdHeader._2}] and " +
                         s"PptReference [$pptReference] is currently unavailable due to [${ex.getMessage}]",
                       ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

}
