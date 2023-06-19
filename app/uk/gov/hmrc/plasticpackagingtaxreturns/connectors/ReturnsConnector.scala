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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status
import play.api.http.Status.{OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector.StatusCode
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{IdDetails, Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ReturnsConnector @Inject() (
  httpClient: HttpClient, 
  override val appConfig: AppConfig, 
  metrics: Metrics, 
  auditConnector: AuditConnector,
  edgeOfSystem: EdgeOfSystem
) (implicit ec: ExecutionContext ) extends EISConnector with Logging {

  val SUCCESS: String = "Success"
  val FAILURE: String = "Failure"

  def submitReturn(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String)
    (implicit hc: HeaderCarrier): Future[Either[Int, Return]] = {

    val timer = metrics.defaultRegistry.timer("ppt.return.create.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> edgeOfSystem.createUuid.toString
    val returnsSubmissionUrl = appConfig.returnsSubmissionUrl(pptReference)
    val requestHeaders = headers :+ correlationIdHeader

    httpClient.PUT[ReturnsSubmissionRequest, HttpResponse](returnsSubmissionUrl, requestBody, requestHeaders)
      .andThen { case _ => timer.stop() }
      .map { jsonResponse =>
        jsonResponse.status match {
          case OK => happyPathSubmit(pptReference, requestBody, internalId, jsonResponse)
          case _ =>
            val json = Try(jsonResponse.json).getOrElse(JsObject.empty)
            if(jsonResponse.status == UNPROCESSABLE_ENTITY
              && (json \ "failures" \ 0 \ "code").asOpt[String].contains("TAX_OBLIGATION_ALREADY_FULFILLED")) {
                auditConnector.sendExplicitAudit(SubmitReturn.eventType,
                  SubmitReturn(internalId, pptReference, SUCCESS, requestBody, Some(Return("date", IdDetails("ppt-ref", "sub-id"), None, None, None)), None))
              Left(StatusCode.RETURN_ALREADY_SUBMITTED)
            } else
              unhappyPathSubmit(pptReference, requestBody, internalId, correlationIdHeader, jsonResponse)
        }
      }
  }

  private def unhappyPathSubmit(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String,
                                correlationIdHeader: (String, String), jsonResponse: HttpResponse)
                               (implicit headerCarrier: HeaderCarrier) = {
    logger.warn(
      s"Upstream error on returns submission with correlationId [${correlationIdHeader._2}], " +
        s"pptReference [$pptReference], and submissionId [${requestBody.submissionId}, status: ${jsonResponse.status}, " +
        s"body: ${jsonResponse.body}",
    )
    auditConnector.sendExplicitAudit(SubmitReturn.eventType,
      SubmitReturn(internalId, pptReference, FAILURE, requestBody, None, Some(jsonResponse.body)))

    Left(jsonResponse.status)
  }

  private def happyPathSubmit(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String,
                              jsonResponse: HttpResponse)(implicit headerCarrier: HeaderCarrier) = {
    Try(Json.parse(jsonResponse.body).as[Return]).toEither.fold({
      throwable =>
        auditConnector.sendExplicitAudit(SubmitReturn.eventType,
          SubmitReturn(internalId, pptReference, FAILURE, requestBody, None, Some(throwable.getMessage)))
        Left(Status.INTERNAL_SERVER_ERROR)
    }, {
      returnResponse =>
        auditConnector.sendExplicitAudit(SubmitReturn.eventType,
            SubmitReturn(internalId, pptReference, SUCCESS, requestBody, Some(returnResponse), None))
        Right(returnResponse)
    })
  }

  def get(pptReference: String, periodKey: String, internalId: String)(implicit hc: HeaderCarrier): Future[Either[Int, JsValue]] = {
    val timer: Timer.Context = metrics.defaultRegistry.timer("ppt.return.display.timer").time()
    val correlationIdHeader: (String, String) = correlationIdHeaderName -> edgeOfSystem.createUuid.toString
    val requestHeaders                        = headers :+ correlationIdHeader

    httpClient.GET[HttpResponse](appConfig.returnsDisplayUrl(pptReference, periodKey), headers = requestHeaders)
      .andThen { case _ => timer.stop() }
      .map { response =>
        logReturnDisplayResponse(pptReference, periodKey, correlationIdHeader, s"status: ${response.status}")

        if (response.status == OK) { // TODO use Status.isSuccessful(x) ?

          Try(response.json).toEither.fold({ 
            throwable =>
              // Note - if response payload was not json, exception from json lib usually includes the payload too
              auditConnector.sendExplicitAudit(GetReturn.eventType, 
                GetReturn(internalId, periodKey, FAILURE, None, Some(throwable.getMessage)))
              Left(Status.INTERNAL_SERVER_ERROR) // TODO should be Status.UNSUPPORTED_MEDIA_TYPE?
          }, { 
            jsValue =>
              auditConnector.sendExplicitAudit(GetReturn.eventType,
                GetReturn(internalId, periodKey, SUCCESS, Some(jsValue), None))
              Right(jsValue)
          })

        } else {
          
          // TODO currently we just parrot down-stream for non 2xx
          auditConnector.sendExplicitAudit(GetReturn.eventType, 
            GetReturn(internalId, periodKey, FAILURE, None, Some(response.body)))
          Left(response.status)
        }
      }
  }

  private def logReturnDisplayResponse(pptReference: String, periodKey: String, correlationIdHeader: (String, String), outcomeMessage: String): Unit = {
    logger.warn(cookLogMessage(pptReference, periodKey, correlationIdHeader, outcomeMessage)
    )
  }

  private def cookLogMessage(pptReference: String, periodKey: String, correlationIdHeader: (String, String), outcomeMessage: String) = {
    s"Return Display API call for correlationId [${correlationIdHeader._2}], " +
      s"pptReference [$pptReference], periodKey [$periodKey]: " + outcomeMessage
  }
}

object ReturnsConnector {
  object StatusCode {
    val RETURN_ALREADY_SUBMITTED = 208
  }
}
