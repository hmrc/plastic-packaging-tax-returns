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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.http.Status
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReads.upstreamResponseMessage
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetPaymentStatement
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.FinancialDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.util.DateAndTime
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FinancialDataConnector @Inject() (
  httpClient: HttpClient, 
  override val appConfig: AppConfig,
  metrics: Metrics, 
  auditConnector: AuditConnector, 
  dateAndTime: DateAndTime
) (
  implicit ec: ExecutionContext
) extends DESConnector {

  private val logger = Logger(this.getClass)

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
    val timer               = metrics.defaultRegistry.timer("ppt.get.financial.data.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> UUID.randomUUID().toString
    val SUCCESS: String     = "Success"
    val FAILURE: String     = "Failure"

    val queryParams: Seq[(String, String)] = QueryParams.fromOptions(
      "dateFrom" -> fromDate.map(DateFormat.isoFormat),
      "dateTo" -> toDate.map(DateFormat.isoFormat),
      "onlyOpenItems" -> onlyOpenItems,
      "includeLocks" -> includeLocks,
      "calculateAccruedInterest" -> calculateAccruedInterest,
      "customerPaymentInformation" -> customerPaymentInformation
    )

    val requestHeaders: Seq[(String, String)] = headers :+ correlationIdHeader

    httpClient.GET[FinancialDataResponse](appConfig.enterpriseFinancialDataUrl(pptReference),
                                          queryParams = queryParams,
                                          headers = requestHeaders
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"Get enterprise financial data with correlationId [$correlationIdHeader._2] pptReference [$pptReference] params [$queryParams]"
        )

        auditConnector.sendExplicitAudit(GetPaymentStatement.eventType,
          GetPaymentStatement(internalId, pptReference, SUCCESS, Some(response), None))

        Right(response)

      }
      .recover {
        case httpEx: UpstreamErrorResponse =>
          inferResponse(httpEx, pptReference).fold[Either[Int, FinancialDataResponse]]({
            logger.warn(
              s"Upstream error returned when getting enterprise financial data correlationId [${correlationIdHeader._2}] and " +
                s"pptReference [$pptReference], params [$queryParams], status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
            )

            auditConnector.sendExplicitAudit(GetPaymentStatement.eventType,
              GetPaymentStatement(internalId, pptReference, FAILURE, None, Some(httpEx.getMessage)))

            Left(httpEx.statusCode)

          })({
            inferredResponse => {

              auditConnector.sendExplicitAudit(GetPaymentStatement.eventType,
                GetPaymentStatement(internalId, pptReference, SUCCESS, Some(inferredResponse), None))

              Right(inferredResponse)

            }

          })
        case ex: Exception =>
          logger.warn(
            s"Failed when getting enterprise financial data with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], params [$queryParams] is currently unavailable due to [${ex.getMessage}]",
            ex
          )

          auditConnector.sendExplicitAudit(GetPaymentStatement.eventType,
            GetPaymentStatement(internalId, pptReference, FAILURE, None, Some(ex.getMessage)))

          Left(INTERNAL_SERVER_ERROR)

      }
  }

  def inferResponse(httpEx: UpstreamErrorResponse, pptReference: String): Option[FinancialDataResponse] = {
    Some(httpEx.statusCode)
      .filter(_ == Status.NOT_FOUND) // only 404s
      .flatMap { _ =>
        upstreamResponseMessage(".*", ".*", 404, "(.*)").r // extract json payload for exception message
          .findFirstMatchIn(httpEx.getMessage())
      }
      .map(_.group(1))
      .flatMap(body => (Json.parse(body) \ "code").asOpt[String]) // try get code field
      .filter(_ == "NOT_FOUND") // if DES code is this -> means no financial records found 
      .map(_ => FinancialDataResponse.inferNoTransactions(pptReference, dateAndTime.currentTime))
  }
}
    
