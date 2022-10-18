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

import com.fasterxml.jackson.annotation.ObjectIdGenerators.UUIDGenerator
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector.EmptyDataMessage
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ObligationsDataConnector @Inject()
(
  httpClient: HttpClient,
  override val appConfig: AppConfig,
  metrics: Metrics,
  auditConnector: AuditConnector,
  uuidGenerator: UUIDGenerator
)(implicit ec: ExecutionContext)
    extends DESConnector with Logging {

  def get(pptReference: String, internalId: String, fromDate: Option[LocalDate], toDate: Option[LocalDate], 
    status: Option[ObligationStatus]) (implicit hc: HeaderCarrier): Future[Either[Int, ObligationDataResponse]] = {
    
    val timer               = metrics.defaultRegistry.timer("ppt.get.obligation.data.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> uuidGenerator.randomUUID
    val correlationId       = correlationIdHeader._2
    val obligationStatus    = status.map(x => x.toString).getOrElse("")
    val requestHeaders: Seq[(String, String)] =  headers :+ correlationIdHeader
    val SUCCESS: String     = "Success"
    val FAILURE: String     = "Failure"

    val queryParams =
      Seq(
        fromDate.map("from" -> DateFormat.isoFormat(_)),
        toDate.map("to" -> DateFormat.isoFormat(_)),
        status.map("status" -> _.toString))
        .flatten

    httpClient.GET[ObligationDataResponse](
      appConfig.enterpriseObligationDataUrl(pptReference),
      queryParams = queryParams,
      headers = requestHeaders
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(s"Get enterprise obligation data with correlationId [$correlationId] pptReference [$pptReference] params [$queryParams]")

        val adjusted = if (appConfig.adjustObligationDates) response.adjustDates else response

        auditConnector.sendExplicitAudit(GetObligations.eventType,
          GetObligations(obligationStatus, internalId, pptReference, SUCCESS, Some(adjusted), None))

        Right(adjusted)
      }
      .recover {
        case Upstream4xxResponse(message, code, _, _) =>
          logUpstreamError(pptReference, correlationId, queryParams, message, code)

          if(isEmptyObligation(message) && code == NOT_FOUND) {

            auditConnector.sendExplicitAudit(GetObligations.eventType,
              GetObligations(obligationStatus, internalId, pptReference, SUCCESS, Some(ObligationDataResponse.empty), None))

            Right(ObligationDataResponse.empty)

          }
          else {

            auditConnector.sendExplicitAudit(GetObligations.eventType,
              GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(message)))

            Left(code)

          }
        case Upstream5xxResponse(message, code, _, _) =>
          logUpstreamError(pptReference, correlationId, queryParams, message, code)

          auditConnector.sendExplicitAudit(GetObligations.eventType,
            GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(message)))

          Left(code)
        case ex: Exception =>
          logger.error(
            s"""Failed when getting enterprise obligation data with correlationId [$correlationId] and """ +
              s"pptReference [$pptReference], params [$queryParams] is currently unavailable due to [${ex.getMessage}]",
            ex
          )

          auditConnector.sendExplicitAudit(GetObligations.eventType,
            GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(ex.getMessage)))

          Left(INTERNAL_SERVER_ERROR)

      }
  }

  private def logUpstreamError
  (
    pptReference: String,
    correlationId: String,
    queryParams: Seq[(String, String)],
    message: String,
    code: Int
  ) = {
    logger.error(
      s"""Error returned when getting enterprise obligation data correlationId [$correlationId] and """ +
        s"pptReference [$pptReference], params [$queryParams], status: $code, body: $message"
    )
  }

  def isEmptyObligation(message: String): Boolean =
    message.contains(EmptyDataMessage)
}

object ObligationsDataConnector {
  val EmptyDataMessage = "The remote endpoint has indicated that no associated data found"
}
