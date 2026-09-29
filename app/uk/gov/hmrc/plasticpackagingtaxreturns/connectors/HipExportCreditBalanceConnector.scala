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

import play.api.Logger
import play.api.http.Status.{CONFLICT, INTERNAL_SERVER_ERROR, OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetExportCredits
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.{
  ExportCreditBalanceDisplayResponse,
  HipExportBalanceDisplayResponseWrapper
}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpClient
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDate
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class HipExportCreditBalanceConnector @Inject() (
  eisHttpClient: EisHttpClient,
  override val appConfig: AppConfig,
  auditConnector: AuditConnector,
  httpClient: HttpClientV2,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends ExportCreditBalanceConnector with HipConnector {

  private val logger                = Logger(this.getClass)
  private val correlationId: String = UUID.randomUUID().toString

  override def getBalance(pptReference: String, fromDate: LocalDate, toDate: LocalDate, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, ExportCreditBalanceDisplayResponse]] = {

    val timer = metrics.defaultRegistry.timer("ppt.exportcreditbalance.display.timer").time()
    val queryParams: Seq[(String, String)] =
      Seq("fromDate" -> DateFormat.isoFormat(fromDate), "toDate" -> DateFormat.isoFormat(toDate))

    httpClient
      .get(appConfig.hipExportCreditBalanceDisplayUrl(pptReference))
      .transform(_.withQueryStringParameters(queryParams: _*))
      .setHeader(hipHeaders(correlationId)*)
      .execute[HttpResponse](readRaw, ec)
      .andThen { case _ => timer.stop() }
      .map { response =>
        (response.status, response.body) match {
          case (OK, body) =>
            handleSuccess(pptReference, fromDate, toDate, internalId, body)
          case (UNPROCESSABLE_ENTITY, body)
              if Try(Json.parse(body)).toOption.exists(js =>
                (js \ "error" \ "errorId").asOpt[String].contains("004")
              ) =>
            val msg =
              s"Upstream error returned on viewing export credit balance with correlationId [$correlationId}] and " +
                s"pptReference [$pptReference], params [$queryParams], status: ${response.status}" +
                s"converting this to a 409 CONFLICT, as that is what was caught by the AvailableCreditService for IFS"
            logger.warn(msg)

            auditConnector.sendExplicitAudit(
              GetExportCredits.eventType,
              GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(s"$msg, body: ${body}"))
            )
            Left(CONFLICT)
          case _ =>
            handleFailure(pptReference, fromDate, toDate, internalId, queryParams, response)
        }
      }
  }

  private def handleFailure(
    pptReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    internalId: String,
    queryParams: Seq[(String, String)],
    response: HttpResponse
  )(implicit hc: HeaderCarrier): Left[Int, Nothing] = {

    val msg =
      s"Upstream error returned on viewing export credit balance with correlationId [$correlationId}] and " +
        s"pptReference [$pptReference], params [$queryParams], status: ${response.status}"
    logger.warn(msg)

    auditConnector.sendExplicitAudit(
      GetExportCredits.eventType,
      GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(s"$msg, body: ${response.body}"))
    )

    Left(response.status)
  }

  private def handleSuccess(
    pptReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    internalId: String,
    body: String
  )(implicit hc: HeaderCarrier): Either[Int, ExportCreditBalanceDisplayResponse] = {
    val triedResponse: Try[HipExportBalanceDisplayResponseWrapper] =
      Try(Json.parse(body).as[HipExportBalanceDisplayResponseWrapper]).recover {
        case exception =>
          throw new RuntimeException(s"Response body could not be read as type Return", exception)
      }

    triedResponse match {
      case Success(balance) =>
        auditConnector.sendExplicitAudit(
          GetExportCredits.eventType,
          GetExportCredits(internalId, pptReference, fromDate, toDate, SUCCESS, Some(balance.success), None)
        )

        Right(balance.success)
      case Failure(exception) =>
        auditConnector.sendExplicitAudit(
          GetExportCredits.eventType,
          GetExportCredits(internalId, pptReference, fromDate, toDate, FAILURE, None, Some(exception.getMessage()))
        )

        Left(INTERNAL_SERVER_ERROR)
    }
  }

}
