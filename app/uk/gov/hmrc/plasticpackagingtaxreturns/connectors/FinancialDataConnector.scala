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
import play.api.libs.json.{JsDefined, JsString}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetPaymentStatement
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildDesHeader
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EdgeOfSystem, EisHttpClient, EisHttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FinancialDataConnector @Inject() (
  eisHttpClient: EisHttpClient,
  appConfig: AppConfig,
  auditConnector: AuditConnector,
  edgeOfSystem: EdgeOfSystem
)(implicit ec: ExecutionContext) {

  private val logger          = Logger(this.getClass)
  private val SUCCESS: String = "Success"
  private val FAILURE: String = "Failure"

  def get(
    pptReference: String,
    fromDate: Option[LocalDate],
    toDate: Option[LocalDate],
    onlyOpenItems: Option[Boolean],
    includeLocks: Option[Boolean],
    calculateAccruedInterest: Option[Boolean],
    customerPaymentInformation: Option[Boolean],
    internalId: String
  )(implicit hc: HeaderCarrier): Future[Either[Int, FinancialDataResponse]] = {
    val timerName = "ppt.get.financial.data.timer"

    val queryParams: Seq[(String, String)] = QueryParams.fromOptions(
      "dateFrom"                   -> fromDate.map(DateFormat.isoFormat),
      "dateTo"                     -> toDate.map(DateFormat.isoFormat),
      "onlyOpenItems"              -> onlyOpenItems,
      "includeLocks"               -> includeLocks,
      "calculateAccruedInterest"   -> calculateAccruedInterest,
      "customerPaymentInformation" -> customerPaymentInformation
    )

    def successFun(response: EisHttpResponse): Boolean =
      response.status match {
        case Status.OK                                                                      => true
        case Status.NOT_FOUND if response.json \ "code" == JsDefined(JsString("NOT_FOUND")) => true
        case _                                                                              => false
      }
    eisHttpClient.get(appConfig.enterpriseFinancialDataUrl(pptReference), queryParams = queryParams, timerName, buildDesHeader, successFun)
      .map { response: EisHttpResponse =>
        response.status match {
          case Status.OK                               => handleSuccess(response, internalId, pptReference)
          case Status.NOT_FOUND if response.isMagic404 => handleMagic404(internalId, pptReference)
          case _                                       => handleErrorResponse(response, pptReference, internalId, queryParams)
        }
      }
  }

  private def handleSuccess(response: EisHttpResponse, internalId: String, pptReference: String)(implicit hc: HeaderCarrier) = {
    val triedResponse = response.jsonAs[FinancialDataResponse]

    triedResponse match {
      case Success(financialData) =>
        auditConnector.sendExplicitAudit(
          GetPaymentStatement.eventType,
          GetPaymentStatement(internalId, pptReference, SUCCESS, Some(financialData), None)
        )

        Right(financialData)
      case Failure(error) =>
        auditConnector.sendExplicitAudit(
          GetPaymentStatement.eventType,
          GetPaymentStatement(internalId, pptReference, FAILURE, None, Some(error.getMessage))
        )

        Left(INTERNAL_SERVER_ERROR)
    }

  }

  def handleMagic404(internalId: String, pptReference: String)(implicit hc: HeaderCarrier) = {
    val inferredResponse = FinancialDataResponse.inferNoTransactions(pptReference, edgeOfSystem.localDateTimeNow)

    auditConnector.sendExplicitAudit(
      GetPaymentStatement.eventType,
      GetPaymentStatement(internalId, pptReference, SUCCESS, Some(inferredResponse), None)
    )

    Right(inferredResponse)
  }

  private def handleErrorResponse(response: EisHttpResponse, pptReference: String, internalId: String, queryParams: Seq[(String, String)])(implicit
    hc: HeaderCarrier
  ) = {

    val message = s"Upstream error returned when getting enterprise financial data with correlationId [${response.correlationId}] and " +
      s"pptReference [$pptReference], params [$queryParams], status: ${response.status}"

    logger.warn(message)

    auditConnector.sendExplicitAudit(GetPaymentStatement.eventType, GetPaymentStatement(internalId, pptReference, FAILURE, None, Some(response.body)))

    Left(response.status)
  }

}
