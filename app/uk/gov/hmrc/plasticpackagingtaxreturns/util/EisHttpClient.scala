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
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
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
) {

  private val EnvironmentHeaderName = "Environment"
  private val CorrelationIdHeaderName = "CorrelationId"

  /**
   * @tparam HappyModel the type of the model payload / request body 
   * @param url full url of endpoint to send put request to
   * @param requestBody object to send in put-request body, must have an implicit json.Writes[A] in-scope
   * @param hc header carrier from up-stream request
   * @param ec current execution context
   * @return [[EisHttpResponse]]
   */
  def put[HappyModel](url: String, requestBody: HappyModel, timerName: String) (implicit hc: HeaderCarrier, ec: ExecutionContext, 
    writes: Writes[HappyModel]): Future[EisHttpResponse] = {

    val correlationId = edgeOfSystem.createUuid.toString
    
    val headers = Seq(
      EnvironmentHeaderName -> appConfig.eisEnvironment, 
      HeaderNames.ACCEPT -> MimeTypes.JSON, 
      HeaderNames.AUTHORIZATION -> appConfig.bearerToken,
      CorrelationIdHeaderName -> correlationId
    )

    val timer = metrics.defaultRegistry.timer(timerName).time()
    hmrcClient
      .PUT[HappyModel, HmrcResponse](url, requestBody, headers)
      .andThen { case _ => timer.stop() }
      .map {
        EisHttpResponse.fromHttpResponse(correlationId) }
  }

}
