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

import play.api.Logging
import play.api.http.Status
import play.api.http.Status.{OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class HipReturnsConnector @Inject() (
  httpClient: HttpClientV2,
  override val appConfig: AppConfig,
  override val auditConnector: AuditConnector,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends ReturnsConnector with HipConnector with Logging {

  def returnsSubmissionUrl(pptReferenceNumber: String) =
    url"${appConfig.hipHost}/etmp/RESTAdapter/plastic-packaging-tax/returns/PPT/$pptReferenceNumber"

  override def get(pptReference: String, periodKey: String, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, JsValue]] = {
    val timer         = metrics.defaultRegistry.timer("ppt.return.display.timer").time()
    val correlationId = UUID.randomUUID().toString

    httpClient
      .get(appConfig.hipReturnsDisplayUrl(pptReference, periodKey))
      .setHeader(hipHeaders(correlationId)*)
      .execute[HttpResponse]
      .andThen { case _ => timer.stop() }
      .map { response =>
        response.status match {
          case Status.OK =>
            Try((Json.parse(response.body) \ "success").as[JsValue]).recover {
              case exception =>
                throw new RuntimeException(s"Response body could not be read as type Return", exception)
            }.toEither match {
              case Right(res) =>
                logReturnDisplayResponse(pptReference, periodKey, correlationId, response.status, response.body)
                auditConnector.sendExplicitAudit(
                  GetReturn.eventType,
                  GetReturn(internalId, periodKey, SUCCESS, Some(response.json), None)
                )
                Right(res)

              case Left(ex) =>
                logReturnDisplayResponse(pptReference, periodKey, correlationId, response.status, response.body)
                auditConnector.sendExplicitAudit(
                  GetReturn.eventType,
                  GetReturn(internalId, periodKey, FAILURE, None, Some(ex.getMessage))
                )
                Left(Status.INTERNAL_SERVER_ERROR)
            }
          case _ =>
            logReturnDisplayResponse(pptReference, periodKey, correlationId, response.status, response.body)
            auditConnector.sendExplicitAudit(
              GetReturn.eventType,
              GetReturn(internalId, periodKey, FAILURE, None, Some(response.body))
            )
            Left(response.status)
        }
      }
  }

  override def submitReturn(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, Return]] = {

    logger.warn(
      s"[submitReturn] Invoked for pptReference [$pptReference] periodKey [${requestBody.periodKey}]"
    )

    val timer         = metrics.defaultRegistry.timer("ppt.return.create.timer").time()
    val correlationId = UUID.randomUUID().toString

    httpClient
      .put(returnsSubmissionUrl(pptReference))
      .withBody(Json.toJson(requestBody))
      .setHeader(hipHeaders(correlationId)*)
      .execute[HttpResponse]
      .andThen { case _ => timer.stop() }
      .map { x =>
        (x.status, x.json, x.headers) match
          case (OK, json, _) =>
            Try((json \ "success").as[Return]).recover {
              case exception =>
                throw new RuntimeException(s"Response body could not be read as type Return", exception)
            }.fold(
              {
                throwable =>
                  logger.warn(
                    s"Return for pptReference=[$pptReference] period=[${requestBody.periodKey}] submitted but failed to" +
                      s" parse response. CorrelationId=[${correlationId}], internalId=[$internalId], error=${throwable.getMessage}"
                  )
                  audit(SubmitReturn(
                    internalId,
                    pptReference,
                    FAILURE,
                    requestBody,
                    None,
                    Some(throwable.getMessage)
                  ))
                  Left(Status.INTERNAL_SERVER_ERROR)
              },
              {
                returnResponse =>
                  logger.warn(
                    s"Return for pptReference=[$pptReference] period=[${requestBody.periodKey}] submitted successfully"
                  )
                  audit(SubmitReturn(
                    internalId,
                    pptReference,
                    SUCCESS,
                    requestBody,
                    Some(returnResponse),
                    None
                  ))
                  Right(returnResponse)
              }
            )

          case (UNPROCESSABLE_ENTITY, json: JsValue, _) if (json \ "error" \ "errorId").asOpt[String].contains("044") =>
            logger.warn(
              s"Return for pptReference=[$pptReference] period=[${requestBody.periodKey}] submission failed " +
                s"with response code=[${UNPROCESSABLE_ENTITY}] internalId=[$internalId]"
            )
            audit(SubmitReturn(internalId, pptReference, SUCCESS, requestBody, None, None))
            Left(RETURN_ALREADY_SUBMITTED)
          case (status, json, _) =>
            logger.warn(
              s"Upstream error during return submission for pptReference=[$pptReference], period=[${requestBody.periodKey}], status=[${status}], CorrelationId=[${correlationId}], internalId=[$internalId]"
            )
            audit(SubmitReturn(internalId, pptReference, FAILURE, requestBody, None, Some(Json.stringify(json))))

            Left(status)
      }
  }

  private def logReturnDisplayResponse(
    pptReference: String,
    periodKey: String,
    correlationId: String,
    status: Int,
    body: String
  ): Unit = logger.warn(cookLogMessage(pptReference, periodKey, correlationId, status, body))

  private def cookLogMessage(
    pptReference: String,
    periodKey: String,
    correlationId: String,
    status: Int,
    body: String
  ) =
    s"Return Display API call for correlationId [${correlationId}], " +
      s"pptReference [$pptReference], periodKey [$periodKey]" +
      s"Hip returned a status of: $status and a body of: $body"

}
