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

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{Return, ReturnsSubmissionRequest}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsConnector @Inject() (httpClient: HttpClient, override val appConfig: AppConfig, metrics: Metrics)(implicit
  ec: ExecutionContext
) extends EISConnector {

  private val logger = Logger(this.getClass)

  def submitReturn(pptReference: String, request: ReturnsSubmissionRequest)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, Return]] = {
    val timer                = metrics.defaultRegistry.timer("ppt.return.create.timer").time()
    val correlationIdHeader  = correlationIdHeaderName -> UUID.randomUUID().toString
    val returnsSubmissionUrl = appConfig.returnsSubmissionUrl(pptReference)

    logger.info(s"Submitting PPT return via $returnsSubmissionUrl")

    httpClient.PUT[ReturnsSubmissionRequest, Return](url = returnsSubmissionUrl,
                                                     headers = headers :+ correlationIdHeader,
                                                     body = request
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"Successful PPT returns submission for request with correlationId [$correlationIdHeader._2] and " +
            s"pptReference [$pptReference], and submissionId [${request.submissionId}]. " +
            s"Response contains submissionId [${response.idDetails.submissionId}]" +
            s"Request body: [${request.returnDetails}]"

        )
        Right(response)
      }
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.warn(
            s"Upstream error on returns submission with correlationId [${correlationIdHeader._2}], " +
              s"pptReference [$pptReference], and submissionId [${request.submissionId}, status: ${httpEx.statusCode}, " +
              s"body: ${httpEx.getMessage()}",
            httpEx
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.warn(
            s"Error on returns submission with correlationId [${correlationIdHeader._2}], " +
              s"pptReference [$pptReference], and submissionId [${request.submissionId}, failed due to [${ex.getMessage}]",
            ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

  def get(pptReference: String, periodKey: String)(implicit hc: HeaderCarrier): Future[Either[Int, JsValue]] = {
    val timer: Timer.Context = metrics.defaultRegistry.timer("ppt.return.display.timer").time()
    val correlationIdHeader: (String, String) = correlationIdHeaderName -> UUID.randomUUID().toString

    httpClient.GET[HttpResponse](appConfig.returnsDisplayUrl(pptReference, periodKey),
      headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logReturnDisplayResponse(pptReference, periodKey, correlationIdHeader, s"status: ${response.status}")
        if (response.status == OK) Right(response.json)
        else Left(response.status)
      }
      .recover {
        case ex: Exception =>
          logger.warn(cookLogMessage(pptReference, periodKey, correlationIdHeader, s"exception: ${ex.getMessage}"), ex)
          Left(INTERNAL_SERVER_ERROR)
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
