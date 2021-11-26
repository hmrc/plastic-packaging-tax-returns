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
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExportCreditBalanceConnector @Inject() (
  httpClient: HttpClient,
  override val appConfig: AppConfig,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends EISConnector {

  private val logger = Logger(this.getClass)

  def getBalance(pptReference: String, fromDate: LocalDate, toDate: LocalDate)(implicit
    hc: HeaderCarrier
  ): Future[Either[Int, ExportCreditBalanceDisplayResponse]] = {
    val timer               = metrics.defaultRegistry.timer("ppt.exportcreditbalance.display.timer").time()
    val correlationIdHeader = correlationIdHeaderName -> UUID.randomUUID().toString

    val queryParams = Seq("fromDate" -> DateFormat.isoFormat(fromDate), "toDate" -> DateFormat.isoFormat(toDate))
    httpClient.GET[ExportCreditBalanceDisplayResponse](appConfig.exportCreditBalanceDisplayUrl(pptReference),
                                                       queryParams = queryParams,
                                                       headers = headers :+ correlationIdHeader
    )
      .andThen { case _ => timer.stop() }
      .map { response =>
        logger.info(
          s"PPT view export credit balance with correlationId [$correlationIdHeader._2] pptReference [$pptReference] params [$queryParams]"
        )
        Right(response)
      }
      .recover {
        case httpEx: UpstreamErrorResponse =>
          logger.warn(
            s"Upstream error returned on viewing export credit balance with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], params [$queryParams], status: ${httpEx.statusCode}, body: ${httpEx.getMessage()}"
          )
          Left(httpEx.statusCode)
        case ex: Exception =>
          logger.warn(
            s"Export credit balance display with correlationId [${correlationIdHeader._2}] and " +
              s"pptReference [$pptReference], params [$queryParams] is currently unavailable due to [${ex.getMessage}]",
            ex
          )
          Left(INTERNAL_SERVER_ERROR)
      }
  }

}
