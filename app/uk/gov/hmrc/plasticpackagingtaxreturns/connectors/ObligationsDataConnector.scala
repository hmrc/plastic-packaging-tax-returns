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
import play.api.Logging
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector.EmptyDataMessage
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
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

    httpClient.GET[HttpResponse](
      appConfig.enterpriseObligationDataUrl(pptReference),
      queryParams = queryParams,
      headers = requestHeaders
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        if(response.status >= 200 && response.status <= 299) {

          logger.info(s"Success on getting enterprise obligation data with correlationId [$correlationId] pptReference [$pptReference] params [$queryParams]")
          val res = Json.fromJson[ObligationDataResponse](response.json).get.adjustDates
          val adjusted = if (appConfig.adjustObligationDates)
            res.adjustDates else res

          auditConnector.sendExplicitAudit(GetObligations.eventType,
            GetObligations(obligationStatus, internalId, pptReference, SUCCESS, Some(adjusted), None))

          Right(res)
        } else if(response.status >= 400 && response.status <= 499) {

          (response.json \ "code").toOption match {
            case Some(a) if a.equals(JsString("NOT_FOUND")) => Right(ObligationDataResponse.empty)
            case _ =>  returnFailure(pptReference, internalId, correlationId, obligationStatus, FAILURE, queryParams, response)
          }
        } else {
          returnFailure(pptReference, internalId, correlationId, obligationStatus, FAILURE, queryParams, response)
        }
      }
  }

  private def returnFailure
  (
    pptReference: String,
    internalId: String,
    correlationId: String,
    obligationStatus: String,
    FAILURE: String,
    queryParams: Seq[(String, String)],
    response: HttpResponse
  )(implicit hc: HeaderCarrier) = {
    val msg = message(pptReference, correlationId, queryParams, response.json.toString(), response.status)

    logger.error(msg)

    auditConnector.sendExplicitAudit(GetObligations.eventType,
      GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(msg)))

    Left(response.status)
  }

  private def message(
                       pptReference: String,
                       correlationId: String,
                       queryParams: Seq[(String, String)],
                       message: String,
                       code: Int) =
    s"""Error returned when getting enterprise obligation data correlationId [$correlationId] and """ +
      s"pptReference [$pptReference], params [$queryParams], status: $code, body: $message"

  def isEmptyObligation(message: String): Boolean =
    message.contains(EmptyDataMessage)
}

object ObligationsDataConnector {
  val EmptyDataMessage = "The remote endpoint has indicated that no associated data found"
}
