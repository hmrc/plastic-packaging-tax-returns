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
  EisReturnsSubmissionRequest,
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

  def submitReturn(pptReference: String, request: EisReturnsSubmissionRequest)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, ReturnsSubmissionResponse]] = {
    val timer               = metrics.defaultRegistry.timer("ppt.return.create.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> UUID.randomUUID().toString

    httpClient.PUT[EisReturnsSubmissionRequest, ReturnsSubmissionResponse](
      url = appConfig.returnsSubmissionUrl(pptReference),
      headers = headers :+ correlationIdHeader,
      body = request
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"PPT create/update return with correlationId [$correlationIdHeader._2] pptReference [$pptReference]"
        )
        Right(response)
      }
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.warn(
            s"Upstream error returned on create return with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.warn(s"Create return with correlationId [${correlationIdHeader._2}] and " +
                        s"pptReference [$pptReference] failed due to [${ex.getMessage}]",
                      ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

}
