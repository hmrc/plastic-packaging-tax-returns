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
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.{Auditor, ChangeSubscriptionEvent}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.ReturnsSubmissionRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.ConnectorISpec
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.{ReturnsTestData, SubscriptionTestData}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnType
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDate, ZonedDateTime}

class AuditorSpec extends ConnectorISpec with ScalaFutures with SubscriptionTestData with ReturnsTestData {

  val auditConnector   = mock[AuditConnector]
  val auditor: Auditor = new Auditor(auditConnector)
  val processingDate   = ZonedDateTime.now()
  val fromDate         = LocalDate.now()
  val toDate           = LocalDate.now.plusDays(1)

  "Auditor" when {

    "registration" should {

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

    "returns" should {

      "post an nrs event" when {

        "nrsReturnSubmitted invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[NrsSubmitReturnEvent])

          val returnSubmissionRequest: ReturnsSubmissionRequest = new ReturnsSubmissionRequest(
            returnType = ReturnType.NEW,
            submissionId = None,
            periodKey = "pkey",
            returnDetails = aReturnWithReturnDetails.returnDetails.get,
            nrsDetails = Some(nrsDetailsSuccess)
          )

          auditor.nrsReturnSubmitted(returnSubmissionRequest, Some(pptReference), Some(processingDate))

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(NrsSubmitReturnEvent.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[NrsSubmitReturnEvent]
          capturedEvent.pptReference mustBe Some(pptReference)
          capturedEvent.returnDetails mustBe returnSubmissionRequest.returnDetails
          capturedEvent.nrsDetails mustBe returnSubmissionRequest.nrsDetails
          capturedEvent.periodKey mustBe returnSubmissionRequest.periodKey
          capturedEvent.processingDateTime mustBe Some(processingDate)

        }
      }

      "post export credits" when {

        "getExportCreditsSuccess invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetExportCredits])

          auditor.getExportCreditsSuccess(
            "testId", pptReference, fromDate, toDate, exportCreditBalanceDisplayResponse
          )

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetExportCredits.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetExportCredits]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Success"
          capturedEvent.fromDate mustBe fromDate
          capturedEvent.toDate mustBe toDate
          capturedEvent.internalId mustBe "testId"
          capturedEvent.response mustBe Some(exportCreditBalanceDisplayResponse)
          capturedEvent.error mustBe None

        }

        "getExportCreditsFailure invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetExportCredits])

          auditor.getExportCreditsFailure(
            "testId", pptReference, fromDate, toDate, "Something went wrong"
          )

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetExportCredits.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetExportCredits]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Failure"
          capturedEvent.fromDate mustBe fromDate
          capturedEvent.toDate mustBe toDate
          capturedEvent.internalId mustBe "testId"
          capturedEvent.response mustBe None
          capturedEvent.error mustBe Some("Something went wrong")

        }
      }

      "post obligations" when {

        "getObligationsSuccess invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetObligations])

          auditor.getObligationsSuccess("Open", "testId", pptReference, Some(obligationDataResponse), pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetObligations.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetObligations]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Success"
          capturedEvent.internalId mustBe "testId"
          capturedEvent.response mustBe Some(obligationDataResponse)
          capturedEvent.error mustBe None
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }

        "getObligationsFailure invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetObligations])

          auditor.getObligationsFailure("Open", "testId", pptReference, "Something went wrong", pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetObligations.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetObligations]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Failure"
          capturedEvent.internalId mustBe "testId"
          capturedEvent.response mustBe None
          capturedEvent.error mustBe Some("Something went wrong")
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }
      }

      "post payment statement" when {

        "getPaymentStatementSuccess invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetPaymentStatement])

          auditor.getPaymentStatementSuccess("testId", pptReference, financials, pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetPaymentStatement.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetPaymentStatement]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.internalId mustBe "testId"
          capturedEvent.result mustBe "Success"
          capturedEvent.response mustBe Some(financials)
          capturedEvent.error mustBe None
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }

        "getPaymentStatementFailure invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetPaymentStatement])

          auditor.getPaymentStatementFailure("testId", pptReference, "Something went wrong", pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetPaymentStatement.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetPaymentStatement]
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.internalId mustBe "testId"
          capturedEvent.result mustBe "Failure"
          capturedEvent.response mustBe None
          capturedEvent.error mustBe Some("Something went wrong")
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }
      }

      "post get return" when {

        "getReturnSuccess invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetReturn])

          auditor.getReturnSuccess("testId", "pkey", Json.toJson(aReturn()), pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetReturn.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetReturn]
          capturedEvent.internalId mustBe "testId"
          capturedEvent.periodKey mustBe "pkey"
          capturedEvent.result mustBe "Success"
          capturedEvent.error mustBe None
          capturedEvent.response mustBe Some(Json.toJson(aReturn()))
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }

        "getReturnFailure invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[GetReturn])

          auditor.getReturnFailure("testId", "pkey", "Something went wrong", pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(GetReturn.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[GetReturn]
          capturedEvent.internalId mustBe "testId"
          capturedEvent.periodKey mustBe "pkey"
          capturedEvent.result mustBe "Failure"
          capturedEvent.error mustBe Some("Something went wrong")
          capturedEvent.response mustBe None
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }
      }

      "post submit return" when {

        "submitReturnSuccess invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[SubmitReturn])

          auditor.submitReturnSuccess("testId", pptReference, aReturnsSubmissionRequest, aReturn(), pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(SubmitReturn.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[SubmitReturn]
          capturedEvent.internalId mustBe "testId"
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Success"
          capturedEvent.response mustBe Some(aReturn())
          capturedEvent.error mustBe None
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }

        "submitReturnFailure invoked" in {

          Mockito.reset(auditConnector)

          val captor = ArgumentCaptor.forClass(classOf[SubmitReturn])

          auditor.submitReturnFailure("testId", pptReference, aReturnsSubmissionRequest, "Something went wrong", pptUserHeaders.toSeq)

          verify(auditConnector, times(1)).
            sendExplicitAudit(eqTo(SubmitReturn.eventType), captor.capture())(any(), any(), any())

          val capturedEvent = captor.getValue.asInstanceOf[SubmitReturn]
          capturedEvent.internalId mustBe "testId"
          capturedEvent.pptReference mustBe pptReference
          capturedEvent.result mustBe "Failure"
          capturedEvent.response mustBe None
          capturedEvent.error mustBe Some("Something went wrong")
          capturedEvent.headers mustBe pptUserHeaders.toSeq

        }
      }
    }
  }
}