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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{FinancialDataResponse, ObligationDataResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscription.Subscription
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDate, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class Auditor @Inject() (auditConnector: AuditConnector) {
  
  val SUCCESS = "Success"
  val FAILURE = "Failure"

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
                              fromDate: LocalDate,
                              toDate: LocalDate,
                              response: ExportCreditBalanceDisplayResponse)
                             (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetExportCredits(internalId, pptReference, fromDate, toDate, SUCCESS, Some(response), None)
    auditConnector.sendExplicitAudit(GetExportCredits.eventType, payload)
  }

  def getExportCreditsFailure(internalId: String,
                              pptReference: String,
                              fromDate: LocalDate,
                              toDate: LocalDate,
                              error: String)
                             (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(error))
    auditConnector.sendExplicitAudit(GetExportCredits.eventType, payload)
  }

  def getObligationsSuccess(obligationType: String,
                            internalId: String,
                            pptReference: String,
                            response: Option[ObligationDataResponse])
                           (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetObligations(obligationType, internalId, pptReference, SUCCESS, response, None)
    auditConnector.sendExplicitAudit(GetObligations.eventType, payload)
  }

  def getObligationsFailure(obligationType: String,
                            internalId: String,
                            pptReference: String,
                            error: String)
                           (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetObligations(obligationType, internalId, pptReference, FAILURE, None, Some(error))
    auditConnector.sendExplicitAudit(GetObligations.eventType, payload)
  }

  def getPaymentStatementSuccess(internalId: String,
                                 pptReference: String,
                                 response: FinancialDataResponse)
                                (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetPaymentStatement(internalId, pptReference, SUCCESS, Some(response), None)
    auditConnector.sendExplicitAudit(GetPaymentStatement.eventType, payload)
  }

  def getPaymentStatementFailure(internalId: String,
                                 pptReference: String,
                                 error: String)
                                (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetPaymentStatement(internalId, pptReference, FAILURE, None, Some(error))
    auditConnector.sendExplicitAudit(GetPaymentStatement.eventType, payload)
  }

  def getReturnSuccess(internalId: String,
                       periodKey: String,
                       response: JsValue)
                      (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetReturn(internalId, periodKey, SUCCESS, Some(response), None)
    auditConnector.sendExplicitAudit(GetReturn.eventType, payload)
  }

  def getReturnFailure(internalId: String,
                       periodKey: String,
                       error: String)
                      (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = GetReturn(internalId, periodKey, FAILURE, None, Some(error))
    auditConnector.sendExplicitAudit(GetReturn.eventType, payload)
  }

  def submitReturnSuccess(internalId: String,
                          pptReference: String,
                          request: ReturnsSubmissionRequest,
                          response: Return)
                         (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitReturn(internalId, pptReference, SUCCESS, request, Some(response), None)
    auditConnector.sendExplicitAudit(SubmitReturn.eventType, payload)
  }

  def submitReturnFailure(internalId: String,
                          pptReference: String,
                          request: ReturnsSubmissionRequest,
                          error: String)
                         (implicit hc: HeaderCarrier, ex: ExecutionContext): Unit = {
    val payload = SubmitReturn(internalId, pptReference, FAILURE, request, None, Some(error))
    auditConnector.sendExplicitAudit(SubmitReturn.eventType, payload)
  }

}
