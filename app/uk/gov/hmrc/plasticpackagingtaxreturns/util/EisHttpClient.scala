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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import com.kenshoo.play.metrics.Metrics
import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.libs.concurrent.Futures
import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EisHttpClient.{retryAttempts, retryDelayInMillisecond}

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.util.Try

/** An http response that allows for equality and same-instance
 * @param status - http status code from response
 * @param body - response body as a [[String]]
 */
case class EisHttpResponse(status: Int, body: String, correlationId: String) {

  /** Tries to parse response body as json
   *
   * @return [[JsObject]] if parse successful, otherwise [[JsNull]] 
   */
  def json: JsValue = Try(Json.parse(body)).getOrElse(JsNull)

  def jsonAs[T](implicit reads: Reads[T], tt: TypeTag[T]): Try[T] = 
    Try(Json.parse(body).as[T]).recover {
      case exception => throw new RuntimeException(s"Response body could not be read as type ${typeOf[T]}", exception)
  }
}

object EisHttpResponse {
  
  /** Create from an hmrc [[HmrcResponse]]
   *
   * @param hmrcResponse  source
   * @param correlationId correlation id for this transaction
   * @return [[EisHttpResponse]]
   * @note does not keep a reference to [[HmrcResponse]]
   */
  def fromHttpResponse(correlationId: String) (hmrcResponse: HmrcResponse): EisHttpResponse = {
    // TODO possibility that we don't set the correlation id for all calls... may need to take this from response header
    EisHttpResponse(hmrcResponse.status, hmrcResponse.body, correlationId)
  }
}

/** Make a rest request-response call to an EIS endpoint or similar. Avoids exceptions for 4xx, 5xx responses. 
 * @note auto-rolled by injector
 * @param hmrcClient underlying hmrc http client to use 
 * @param appConfig source for required header field values 
 * @param edgeOfSystem used to create a new UUID for each transaction
 * @param metrics source for request-response transaction timer
 */
class EisHttpClient @Inject() (
  hmrcClient: HmrcClient,
  appConfig: AppConfig,
  edgeOfSystem: EdgeOfSystem,
  metrics: Metrics,
  futures: Futures
) (implicit executionContext: ExecutionContext) {

  private val EnvironmentHeaderName = "Environment"
  private val CorrelationIdHeaderName = "CorrelationId"


  type SuccessFun = EisHttpResponse => Boolean
  private val isSuccessful: SuccessFun = response => Status.isSuccessful(response.status)

  /**
   * @tparam HappyModel the type of the model payload / request body 
   * @param url full url of endpoint to send put request to
   * @param requestBody object to send in put-request body, must have an implicit json.Writes[A] in-scope
   * @param hc header carrier from up-stream request
   * @return [[EisHttpResponse]]
   */
  def put[HappyModel](url: String, requestBody: HappyModel, timerName: String, successFun: SuccessFun = isSuccessful)
    (implicit hc: HeaderCarrier, writes: Writes[HappyModel]): Future[EisHttpResponse] = {

    val correlationId = edgeOfSystem.createUuid.toString

    val putFunction = () =>
      hmrcClient.PUT[HappyModel, HmrcResponse](url, requestBody, buildHeaders(correlationId))
        .map {
          EisHttpResponse.fromHttpResponse(correlationId)
        }

    val timer = metrics.defaultRegistry.timer(timerName).time()
    retry(retryAttempts, putFunction, successFun)
      .andThen { case _ => timer.stop() }
  }

  //todo this get is for DES at the moment as it uses the bearertoken for DES.
  // Could make this more generic and accept a bearer token for both EIS and DES
  def get(url: String, queryParams: Seq[(String, String)], timerName: String, successFun: SuccessFun = isSuccessful)(implicit hc: HeaderCarrier):Future[EisHttpResponse]  = {
    val timer = metrics.defaultRegistry.timer(timerName).time()
    val correlationId = edgeOfSystem.createUuid.toString


    val getFunction = () =>
      hmrcClient.GET(url, queryParams, buildDesHeaders(correlationId)).map {
        EisHttpResponse.fromHttpResponse(correlationId)
      }

    retry(retryAttempts, getFunction, successFun)
      .andThen{ case _ => timer.stop() }
  }

  def retry(times: Int, function: () => Future[EisHttpResponse], successFun: SuccessFun): Future[EisHttpResponse] =
    function()
      .flatMap {
        case response if successFun(response) => Future.successful(response)
        case response if times == 1 => Future.successful(response)
        case _ => futures
          .delay(retryDelayInMillisecond milliseconds)
          .flatMap { _ => retry(times - 1, function, successFun) }
      }

  private def buildHeaders(correlationId: String): Seq[(String, String)] = {
    Seq(
      EnvironmentHeaderName -> appConfig.eisEnvironment,
      HeaderNames.ACCEPT -> MimeTypes.JSON,
      HeaderNames.AUTHORIZATION -> appConfig.bearerToken,
      CorrelationIdHeaderName -> correlationId
    )
  }

  private def buildDesHeaders(correlationId: String): Seq[(String, String)] = {
    Seq(
      EnvironmentHeaderName -> appConfig.eisEnvironment,
      HeaderNames.ACCEPT -> MimeTypes.JSON,
      HeaderNames.AUTHORIZATION -> appConfig.desBearerToken,
      CorrelationIdHeaderName -> correlationId
    )
  }

}

object EisHttpClient {
  val retryDelayInMillisecond = 1000
  val retryAttempts = 3
}
