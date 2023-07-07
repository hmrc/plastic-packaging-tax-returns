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

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EisHttpClient, EisHttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class SubscriptionsConnector @Inject()
(
  eisHttpClient: EisHttpClient,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)  {

  private val logger = Logger(this.getClass)

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateSuccessfulResponse] = {
    val timerName = "ppt.subscription.update.timer"

    eisHttpClient.put(
      appConfig.subscriptionUpdateUrl(pptReference),
      subscriptionUpdateDetails,
      timerName,
      buildEisHeader
    ).map { response =>

      response.status match {
        case Status.OK =>
          val triedResponse = response.jsonAs[SubscriptionUpdateSuccessfulResponse]

          triedResponse match {
            case Success(subscription) =>
              logger.info(
                s"PPT subscription update sent with correlationId [${response.correlationId}] and pptReference [$pptReference]"
              )
              subscription
            case Failure(exception) => throwException(pptReference, response.correlationId, exception)
          }
        case _ => throwException(pptReference, response.correlationId, response.body)
      }
    }
  }

  private def throwException(pptReference: String, correlationId: String, exception: Throwable) = {
    throw new Exception(
      s"Subscription update with correlationId [$correlationId] and " +
        s"pptReference [$pptReference] is currently unavailable due to [${exception.getMessage}]",
      exception
    )
  }

  private def throwException(pptReference: String, correlationId: String, body: String) = {
    throw new Exception(
      s"Subscription update with correlationId [$correlationId] and " +
        s"pptReference [$pptReference] is currently unavailable due to [$body]",
    )
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
