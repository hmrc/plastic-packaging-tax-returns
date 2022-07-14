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

package uk.gov.hmrc.plasticpackagingtaxreturns.auditor

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.{Auditor, ChangeSubscriptionEvent}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.ConnectorISpec
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.SubscriptionTestData
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.ZonedDateTime

class Auditor2Spec extends ConnectorISpec with ScalaFutures with SubscriptionTestData {

  val auditConnector   = mock[AuditConnector]
  val auditor: Auditor = new Auditor(auditConnector)
  val processingDate   = ZonedDateTime.now()

  "Auditor" should {
    "post registration change event" when {

      "subscriptionUpdate invoked" in {

        Mockito.reset(auditConnector)

        val subscriptionUpdate: Subscription = createSubscriptionUpdateRequest(ukLimitedCompanySubscription).toSubscription

        val captor = ArgumentCaptor.forClass(classOf[ChangeSubscriptionEvent])

        auditor.subscriptionUpdated(subscriptionUpdate, pptReference = Some("pptReference"), Some(processingDate))

        verify(auditConnector, times(1)).
          sendExplicitAudit(eqTo(ChangeSubscriptionEvent.eventType), captor.capture())(any(), any(), any())

        val capturedEvent = captor.getValue.asInstanceOf[ChangeSubscriptionEvent]

        capturedEvent.pptReference mustBe Some("pptReference")
        capturedEvent.nrsDetails mustBe subscriptionUpdate.nrsDetails
        capturedEvent.legalEntityDetails mustBe subscriptionUpdate.legalEntityDetails
        capturedEvent.declaration mustBe subscriptionUpdate.declaration
        capturedEvent.businessCorrespondenceDetails mustBe subscriptionUpdate.businessCorrespondenceDetails
        capturedEvent.changeOfCircumstanceDetails mustBe subscriptionUpdate.changeOfCircumstanceDetails
        capturedEvent.groupSubscription mustBe subscriptionUpdate.groupPartnershipSubscription
        capturedEvent.last12MonthTotalTonnageAmt mustBe subscriptionUpdate.last12MonthTotalTonnageAmt
        capturedEvent.principalPlaceOfBusinessDetails mustBe subscriptionUpdate.principalPlaceOfBusinessDetails
        capturedEvent.processingDateTime mustBe Some(processingDate)
        capturedEvent.taxObligationStartDate mustBe subscriptionUpdate.taxObligationStartDate

      }
    }
  }
}