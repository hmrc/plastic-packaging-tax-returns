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

import java.time.LocalDate
import java.util.UUID

import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import play.api.Logger
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus

import scala.concurrent.{ExecutionContext, Future}

class ObligationDataConnector @Inject() (httpClient: HttpClient, override val appConfig: AppConfig, metrics: Metrics)(
  implicit ec: ExecutionContext
) extends DESConnector {

  private val logger = Logger(this.getClass)

  def get(pptReference: String, fromDate: LocalDate, toDate: LocalDate, status: ObligationStatus)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, ObligationDataResponse]] = {
    val timer               = metrics.defaultRegistry.timer("ppt.get.obligation.data.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> UUID.randomUUID().toString

    val queryParams =
      Seq("fromDate" -> DateFormat.isoFormat(fromDate),
          "toDate"   -> DateFormat.isoFormat(toDate),
          "status"   -> status.toString
      )

    httpClient.GET[ObligationDataResponse](appConfig.enterpriseObligationDataUrl(pptReference),
                                           queryParams = queryParams,
                                           headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"Get enterprise obligation data with correlationId [$correlationIdHeader._2] pptReference [$pptReference] params [$queryParams]"
        )
        Right(response)
      }
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.warn(
            s"Upstream error returned when getting enterprise obligation data correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], params [$queryParams], status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.warn(
            s"Failed when getting enterprise obligation data with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], params [$queryParams] is currently unavailable due to [${ex.getMessage}]",
            ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

}
