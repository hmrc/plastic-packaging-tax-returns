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

import play.api.Logging
import play.api.http.Status
import play.api.http.Status.{OK, UNPROCESSABLE_ENTITY}
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.{GetReturn, SubmitReturn}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector.StatusCode
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnsSubmissionRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EisHttpClient, EisHttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReturnsConnector @Inject()
(
  appConfig: AppConfig,
  auditConnector: AuditConnector,
  eisHttpClient: EisHttpClient
) (implicit ec: ExecutionContext ) extends Logging {

  val SUCCESS: String = "Success"
  val FAILURE: String = "Failure"

  def submitReturn(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String)
    (implicit hc: HeaderCarrier): Future[Either[Int, Return]] = {

    def isSuccessful(response: EisHttpResponse): Boolean = response.status match {
      case Status.OK => true
      case Status.UNPROCESSABLE_ENTITY =>
        val jsLookupResult = response.json \ "failures" \ 0 \ "code"
        jsLookupResult
          .asOpt[String]
          .contains("TAX_OBLIGATION_ALREADY_FULFILLED")
      case _ => false
    }

    val returnsSubmissionUrl = appConfig.returnsSubmissionUrl(pptReference)
    eisHttpClient.put(returnsSubmissionUrl, requestBody, "ppt.return.create.timer", buildEisHeader, isSuccessful)
      .map { httpResponse =>

        if (httpResponse.status == OK)
          happyPathSubmit(pptReference, requestBody, internalId, httpResponse)

        else if(httpResponse.status == UNPROCESSABLE_ENTITY
          && (httpResponse.json \ "failures" \ 0 \ "code").asOpt[String].contains("TAX_OBLIGATION_ALREADY_FULFILLED")) {
          auditConnector.sendExplicitAudit(SubmitReturn.eventType,
            SubmitReturn(internalId, pptReference, SUCCESS, requestBody, None, None))
          Left(StatusCode.RETURN_ALREADY_SUBMITTED)
        } else
          unhappyPathSubmit(pptReference, requestBody, internalId, httpResponse)
      }
  }

  private def unhappyPathSubmit(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String,
    httpResponse: EisHttpResponse) (implicit headerCarrier: HeaderCarrier) = {

    logger.warn(
      s"Upstream error on returns submission with correlationId [${httpResponse.correlationId}], " +
        s"pptReference [$pptReference], and submissionId [${requestBody.submissionId}, status: ${httpResponse.status}, " +
        s"body: ${httpResponse.body}",
    )
    auditConnector.sendExplicitAudit(SubmitReturn.eventType,
      SubmitReturn(internalId, pptReference, FAILURE, requestBody, None, Some(httpResponse.body)))

    Left(httpResponse.status)
  }

  private def happyPathSubmit(pptReference: String, requestBody: ReturnsSubmissionRequest, internalId: String,
                              eisHttpResponse: EisHttpResponse)(implicit headerCarrier: HeaderCarrier) = {

    eisHttpResponse.jsonAs[Return].fold({
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
    val timerName = "ppt.return.display.timer"

    eisHttpClient.get(appConfig.returnsDisplayUrl(pptReference, periodKey), Seq.empty, timerName, buildEisHeader)
      .map { response =>
        logReturnDisplayResponse(pptReference, periodKey, response.correlationId, s"status: ${response.status}")

        response.status match {
          case Status.OK =>
            val triedResponse = response.jsonAs[JsValue]

            triedResponse match {
              case Success(jsValue) =>
                auditConnector.sendExplicitAudit(GetReturn.eventType,
                GetReturn(internalId, periodKey, SUCCESS, Some(jsValue), None))
                Right(jsValue)
              case Failure(exception) =>
                // Note - if response payload was not json, exception from json lib usually includes the payload too
                auditConnector.sendExplicitAudit(GetReturn.eventType,
                  GetReturn(internalId, periodKey, FAILURE, None, Some(exception.getMessage)))
                Left(Status.INTERNAL_SERVER_ERROR)
            }
          case _ =>
            auditConnector.sendExplicitAudit(GetReturn.eventType,
              GetReturn(internalId, periodKey, FAILURE, None, Some(response.body)))
            Left(response.status)
        }
      }
  }

  private def logReturnDisplayResponse(pptReference: String, periodKey: String, correlationId: String, outcomeMessage: String): Unit = {
    logger.warn(cookLogMessage(pptReference, periodKey, correlationId, outcomeMessage)
    )
  }

  private def cookLogMessage(pptReference: String, periodKey: String, correlationId: String, outcomeMessage: String) = {
    s"Return Display API call for correlationId [${correlationId}], " +
      s"pptReference [$pptReference], periodKey [$periodKey]: " + outcomeMessage
  }
}

object ReturnsConnector {
  object StatusCode {
    val RETURN_ALREADY_SUBMITTED = 208
  }
}
