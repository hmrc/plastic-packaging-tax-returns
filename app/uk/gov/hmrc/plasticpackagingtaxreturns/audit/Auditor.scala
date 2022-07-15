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

package uk.gov.hmrc.plasticpackagingtaxreturns.audit

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.ReturnsSubmissionRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.plasticpackagingtaxreturns.models.PPTFinancials
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

object Result extends Enumeration {
  type Result = Value

  val Success, Failure = Value.toString
}

@Singleton
class Auditor @Inject() (auditConnector: AuditConnector) {

  def subscriptionUpdated(
    subscription: Subscription,
    pptReference: Option[String] = None,
    processingDateTime: Option[ZonedDateTime] = None
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Unit =
    auditConnector.sendExplicitAudit(ChangeSubscriptionEvent.eventType,
                                     ChangeSubscriptionEvent(subscription, pptReference, processingDateTime))

  def nrsReturnSubmitted(submission: ReturnsSubmissionRequest,
                         pptReference: Option[String] = None,
                         processingDateTime: Option[ZonedDateTime] = None)
                        (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit =
    auditConnector.sendExplicitAudit(NrsSubmitReturnEvent.eventType, NrsSubmitReturnEvent(submission, pptReference, processingDateTime))

  def getExportCreditsSuccess(internalId: String,
                              pptReference: String,
                              fromDate: ZonedDateTime,
                              toDate: ZonedDateTime,
                              response: ExportCreditBalanceDisplayResponse,
                              headers: Seq[(String, String)])
                             (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetExportCredits(internalId, pptReference, fromDate, toDate, Result.Success, Some(response), None, headers)
    auditConnector.sendExplicitAudit(GetExportCredits.eventType, payload)
  }

  def getExportCreditsFailure(internalId: String,
                              pptReference: String,
                              fromDate: ZonedDateTime,
                              toDate: ZonedDateTime,
                              error: String,
                              headers: Seq[(String, String)])
                             (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetExportCredits(internalId, pptReference, fromDate, toDate, Result.Failure, None, Some(error), headers)
    auditConnector.sendExplicitAudit(GetExportCredits.eventType, payload)
  }

  def getObligationsSuccess(obligationType: String,
                            internalId: String,
                            pptReference: String,
                            response: Seq[ObligationDataResponse],
                            headers: Seq[(String, String)])
                           (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetObligations(obligationType, internalId, pptReference, Result.Success, Some(response), None, headers)
    auditConnector.sendExplicitAudit(GetObligations.eventType, payload)
  }

  def getObligationsFailure(obligationType: String,
                            internalId: String,
                            pptReference: String,
                            error: String,
                            headers: Seq[(String, String)])
                           (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetObligations(obligationType, internalId, pptReference, Result.Failure, None, Some(error), headers)
    auditConnector.sendExplicitAudit(GetObligations.eventType, payload)
  }

  def getPaymentStatementSuccess(internalId: String,
                                 pptReference: String,
                                 response: PPTFinancials,
                                 headers: Seq[(String, String)])
                                (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetPaymentStatement(internalId, pptReference, Result.Success, Some(response), None, headers)
    auditConnector.sendExplicitAudit(GetPaymentStatement.eventType, payload)
  }

  def getPaymentStatementFailure(internalId: String,
                                 pptReference: String,
                                 error: String,
                                 headers: Seq[(String, String)])
                                (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetPaymentStatement(internalId, pptReference, Result.Failure, None, Some(error), headers)
    auditConnector.sendExplicitAudit(GetPaymentStatement.eventType, payload)
  }

  def getReturnSuccess(internalId: String,
                       periodKey: String,
                       response: JsValue,
                       headers: Seq[(String, String)])
                      (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetReturn(internalId, periodKey, Result.Success, Some(response), None, headers)
    auditConnector.sendExplicitAudit(GetReturn.eventType, payload)
  }

  def getReturnFailure(internalId: String,
                       periodKey: String,
                       error: String,
                       headers: Seq[(String, String)])
                      (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetReturn(internalId, periodKey, Result.Failure, None, Some(error), headers)
    auditConnector.sendExplicitAudit(GetReturn.eventType, payload)
  }

  def submitAmendSuccess(internalId: String,
                         pptReference: String,
                         request: ReturnsSubmissionRequest,
                         response: JsValue,
                         headers: Seq[(String, String)])
                        (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitAmend(internalId, pptReference, Result.Success, request, Some(response), None, headers)
    auditConnector.sendExplicitAudit(SubmitAmend.eventType, payload)
  }

  def submitAmendFailure(internalId: String,
                         pptReference: String,
                         request: ReturnsSubmissionRequest,
                         error: String,
                         headers: Seq[(String, String)])
                        (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitAmend(internalId, pptReference, Result.Failure, request, None, Some(error), headers)
    auditConnector.sendExplicitAudit(SubmitAmend.eventType, payload)
  }

  def submitReturnSuccess(internalId: String,
                          pptReference: String,
                          request: ReturnsSubmissionRequest,
                          response: JsValue,
                          headers: Seq[(String, String)])
                         (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitReturn(internalId, pptReference, Result.Success, request, Some(response), None, headers)
    auditConnector.sendExplicitAudit(SubmitReturn.eventType, payload)
  }

  def submitReturnFailure(internalId: String,
                          pptReference: String,
                          request: ReturnsSubmissionRequest,
                          error: String,
                          headers: Seq[(String, String)])
                         (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitReturn(internalId, pptReference, Result.Failure, request, None, Some(error), headers)
    auditConnector.sendExplicitAudit(SubmitReturn.eventType, payload)
  }

}
