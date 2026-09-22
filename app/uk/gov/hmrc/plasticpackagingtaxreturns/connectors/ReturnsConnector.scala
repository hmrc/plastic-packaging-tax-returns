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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.SubmitReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

trait ReturnsConnector {

  val auditConnector: AuditConnector
  val SUCCESS: String = "Success"
  val FAILURE: String = "Failure"

  def get(pptReference: String, periodKey: String, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, JsValue]]

  def submitReturn(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, Return]]

  val RETURN_ALREADY_SUBMITTED: Int = ReturnsConnector.StatusCode.RETURN_ALREADY_SUBMITTED

  def audit(submitReturn: SubmitReturn)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): Unit = auditConnector.sendExplicitAudit(SubmitReturn.eventType, submitReturn)

}

object ReturnsConnector {

  object StatusCode {
    val RETURN_ALREADY_SUBMITTED = 208
  }

}
