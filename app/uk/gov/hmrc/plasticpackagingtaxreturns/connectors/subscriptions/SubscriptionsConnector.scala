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

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{SubscriptionUpdateRequest, SubscriptionUpdateSuccessfulResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpResponse

import scala.concurrent.Future

trait SubscriptionsConnector {

  val logger: Logger = Logger(this.getClass)

  def updateSubscription(pptReference: String, subscriptionUpdateDetails: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateSuccessfulResponse]

  def getSubscription(pptReference: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[EisHttpResponse, SubscriptionDisplayResponse]]

  def getSubscriptionFuture(pptReference: String)(implicit hc: HeaderCarrier): Future[SubscriptionDisplayResponse]

  def throwException(pptReference: String, correlationId: String, exception: Throwable) =
    throw new Exception(
      s"Subscription update with correlationId [$correlationId] and " +
        s"pptReference [$pptReference] is currently unavailable due to [${exception.getMessage}]",
      exception
    )

  def throwException(pptReference: String, correlationId: String, status: Int) =
    throw new Exception(
      s"Subscription update with correlationId [$correlationId] and " +
        s"pptReference [$pptReference] is currently unavailable. Status code is [$status]"
    )

}
