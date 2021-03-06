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

package uk.gov.hmrc.plasticpackagingtaxreturns.audit

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class Auditor @Inject() (auditConnector: AuditConnector) {

  def subscriptionUpdated(
    subscription: Subscription,
    pptReference: Option[String] = None,
    processingDateTime: Option[ZonedDateTime] = None
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Unit =
    auditConnector.sendExplicitAudit(ChangeSubscriptionEvent.eventType,
                                     ChangeSubscriptionEvent(subscription, pptReference, processingDateTime)
    )

}
