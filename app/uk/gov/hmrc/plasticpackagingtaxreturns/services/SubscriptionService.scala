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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import com.google.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{
  SubscriptionUpdateRequest,
  SubscriptionUpdateSuccessfulResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.subscriptions.{
  EisSubscriptionsConnector,
  HipSubscriptionsConnector,
  SubscriptionsConnector
}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpResponse

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionService @Inject() (
  eisSubscriptionsConnector: EisSubscriptionsConnector,
  hipSubscriptionConnector: HipSubscriptionsConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) {

  private lazy val connector: SubscriptionsConnector =
    if (appConfig.hipReturns) hipSubscriptionConnector
    else eisSubscriptionsConnector

  def getSubscription(
    pptReference: String
  )(implicit hc: HeaderCarrier): Future[Either[EisHttpResponse, SubscriptionDisplayResponse]] =
    connector.getSubscription(pptReference)

  def getSubscriptionFuture(pptReference: String)(implicit hc: HeaderCarrier): Future[SubscriptionDisplayResponse] =
    connector.getSubscriptionFuture(pptReference)

  def updateSubscription(pptReference: String, subscription: SubscriptionUpdateRequest)(implicit
    hc: HeaderCarrier
  ): Future[SubscriptionUpdateSuccessfulResponse] = connector.updateSubscription(pptReference, subscription)

}
