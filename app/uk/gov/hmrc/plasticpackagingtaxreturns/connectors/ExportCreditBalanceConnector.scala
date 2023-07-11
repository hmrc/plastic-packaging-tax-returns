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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpClient
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ExportCreditBalanceConnector @Inject() (
  eisHttpClient: EisHttpClient,
  override val appConfig: AppConfig,
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends EISConnector {

  private val logger = Logger(this.getClass)

  def getBalance(pptReference: String, fromDate: LocalDate, toDate: LocalDate, internalId: String)(implicit
                                                                                                   hc: HeaderCarrier
  ): Future[Either[Int, ExportCreditBalanceDisplayResponse]] = {

    val timerName: String = "ppt.exportcreditbalance.display.timer"
    val queryParams: Seq[(String, String)] = Seq("fromDate" -> DateFormat.isoFormat(fromDate), "toDate" -> DateFormat.isoFormat(toDate))
    val SUCCESS: String = "Success"
    val FAILURE: String = "Failure"

    eisHttpClient.get(appConfig.exportCreditBalanceDisplayUrl(pptReference),
      queryParams = queryParams,
      timerName,
      buildEisHeader
    ).map { response =>
      response.status match {
        case Status.OK =>
          val triedResponse = response.jsonAs[ExportCreditBalanceDisplayResponse]

          triedResponse match {
            case Success(balance) =>
              auditConnector.sendExplicitAudit(GetExportCredits.eventType,
                GetExportCredits(internalId, pptReference, fromDate, toDate, SUCCESS, Some(balance), None))

              Right(balance)
            case Failure(exception) =>

              auditConnector.sendExplicitAudit(GetExportCredits.eventType,
                GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(exception.getMessage())))

              Left(INTERNAL_SERVER_ERROR)

          }
        case _ =>

          val msg = s"Upstream error returned on viewing export credit balance with correlationId [${response.correlationId}] and " +
            s"pptReference [$pptReference], params [$queryParams], status: ${response.status}"
          logger.warn(msg)

          auditConnector.sendExplicitAudit(GetExportCredits.eventType,
            GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(s"$msg, body: ${response.body}")))

          Left(response.status)
      }
    }
  }
}
