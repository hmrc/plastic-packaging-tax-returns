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
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.given
import uk.gov.hmrc.http.client.{HttpClientV2, given}
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.Result
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton

class NRSConnector @Inject() (
                                            httpClient: HttpClientV2,
                                            appConfig: AppConfig
                                          )(using ExecutionContext)
  extends Logging:

  def invalidSubmit: Future[Result] =
    given HeaderCarrier = HeaderCarrier()

    val submissionUrl = appConfig.nonRepudiationSubmissionUrl

    httpClient
      .post(url"$submissionUrl")
      .withBody(Json.obj())
      .setHeader(
        "X-API-Key" -> appConfig.nonRepudiationApiKeyNew
      ).execute[HttpResponse]
      .map { response =>
        response.status match
          case ACCEPTED =>
            logger.warn("received ACCEPTED response for invalid submission")
            Result.Successful
          case FORBIDDEN | UNAUTHORIZED =>
            logger.warn("received FORBIDDEN or UNAUTHORIZED response for invalid submission")
            Result.Unauthorized
          case BAD_REQUEST =>
            logger.warn("received BAD_REQUEST response for invalid submission")
            Result.BadRequest
          case status if status >= 500 =>
            logger.warn(s"received server error response ($status) for invalid submission")
            Result.ServerError(status)
          case status =>
            logger.warn(s"Unexpected response status code $status returned")
            Result.OtherError(status, if response.body.nonEmpty then Some(response.body) else None)
      }
      .recover {
        case exception: GatewayTimeoutException => Result.NetworkError(exception.message, exception)
        case exception: BadGatewayException => Result.NetworkError(exception.message, exception)
      }