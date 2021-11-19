/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.{
  ReturnsSubmissionRequest,
  ReturnsSubmissionResponse
}

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
  ): Future[Either[Int, ReturnsSubmissionResponse]] = {
    val timer                = metrics.defaultRegistry.timer("ppt.return.create.timer").time()
    val correlationIdHeader  = correlationIdHeaderName -> UUID.randomUUID().toString
    val returnsSubmissionUrl = appConfig.returnsSubmissionUrl(pptReference)

    logger.info(s"Submitting PPT return via $returnsSubmissionUrl")

    httpClient.PUT[ReturnsSubmissionRequest, ReturnsSubmissionResponse](url = returnsSubmissionUrl,
                                                                        headers = headers :+ correlationIdHeader,
                                                                        body = request
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"Successful PPT returns submission for request with correlationId [$correlationIdHeader._2] and " +
            s"pptReference [$pptReference], and submissionId [${request.submissionId}]. " +
            s"Response contains submissionId [${response.idDetails.submissionId}]"
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

}
