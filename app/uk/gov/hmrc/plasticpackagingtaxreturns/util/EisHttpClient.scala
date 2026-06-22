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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import play.api.Logging
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.*
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse as HmrcResponse, StringContextOps}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import izumi.reflect.Tag
import scala.util.Try
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

/** An http response that allows for equality and same-instance
  * @param status
  *   \- http status code from response
  * @param body
  *   \- response body as a [[String]]
  */
case class EisHttpResponse(status: Int, body: String, correlationId: String) {

  /** Tries to parse response body as json
    *
    * @return
    *   [[JsObject]] if parse successful, otherwise [[JsNull]]
    */
  def json: JsValue = Try(Json.parse(body)).getOrElse(JsNull)

  /** Tries to read response body as json of given type
    * @tparam T
    *   type to read json body as
    * @return
    *   a successful [[Try]] with T, or a failed Try with exception chain
    * @note
    *   careful logging on failure, as exception chain may contain parts of the response body
    */
  def jsonAs[T](implicit reads: Reads[T], tt: Tag[T]): Try[T] =
    Try(Json.parse(body).as[T]).recover {
      case exception =>
        throw new RuntimeException(s"Response body could not be read as type ${tt.tag.longNameWithPrefix}", exception)
    }

  /** Detect is this is a HTTP 404 or a case of empty data
    *
    * @return
    *   [[true]] if data is empty, otherwise [[false]] if is an HTTP 404
    */
  def isMagic404: Boolean = status == NOT_FOUND && Json.parse(body) \ "code" == JsDefined(JsString("NOT_FOUND"))

}

object EisHttpResponse {

  /** Create from an hmrc [[HmrcResponse]]
    *
    * @param hmrcResponse
    *   source
    * @param correlationId
    *   correlation id for this transaction
    * @return
    *   [[EisHttpResponse]]
    * @note
    *   does not keep a reference to [[HmrcResponse]]
    */
  def fromHttpResponse(correlationId: String)(hmrcResponse: HmrcResponse): EisHttpResponse =
    EisHttpResponse(hmrcResponse.status, hmrcResponse.body, correlationId)

}

/** Make a rest request-response call to an EIS endpoint or similar. Avoids exceptions for 4xx, 5xx responses.
  * @note
  *   auto-rolled by injector
  * @param hmrcClient
  *   underlying hmrc http client to use
  * @param appConfig
  *   source for required header field values
  * @param edgeOfSystem
  *   used to create a new UUID for each transaction
  * @param metrics
  *   source for request-response transaction timer
  */
class EisHttpClient @Inject() (
  hmrcClient: HttpClientV2,
  appConfig: AppConfig,
  edgeOfSystem: EdgeOfSystem,
  metrics: Metrics
)(implicit executionContext: ExecutionContext)
    extends Logging {

  /** @tparam HappyModel
    *   the type of the model payload / request body
    * @param url
    *   full url of endpoint to send put request to
    * @param requestBody
    *   object to send in put-request body, must have an implicit json.Writes[A] in-scope
    * @param hc
    *   header carrier from up-stream request
    * @return
    *   [[EisHttpResponse]]
    */
  def put[HappyModel](
    url: String,
    requestBody: HappyModel,
    timerName: String,
    headerFun: (String, AppConfig) => Seq[(String, String)]
  )(implicit hc: HeaderCarrier, writes: Writes[HappyModel]): Future[EisHttpResponse] = {

    val correlationId = edgeOfSystem.createUuid.toString
    val timer         = metrics.defaultRegistry.timer(timerName).time()

    hmrcClient.put(url"$url").setHeader(headerFun(correlationId, appConfig): _*).withBody(
      Json.toJson(requestBody)
    ).execute[HmrcResponse]
      .map(EisHttpResponse.fromHttpResponse(correlationId))
      .andThen { case _ => timer.stop() }
  }

  def get(
    url: String,
    queryParams: Seq[(String, String)],
    timerName: String,
    headerFun: (String, AppConfig) => Seq[(String, String)]
  )(implicit hc: HeaderCarrier): Future[EisHttpResponse] = {
    val correlationId = edgeOfSystem.createUuid.toString
    val timer         = metrics.defaultRegistry.timer(timerName).time()

    hmrcClient.get(url"$url").transform(_.withQueryStringParameters(queryParams: _*)).setHeader(
      headerFun(correlationId, appConfig): _*
    ).execute[HmrcResponse]
      .map(EisHttpResponse.fromHttpResponse(correlationId))
      .andThen { case _ => timer.stop() }
  }

}

object EisHttpClient {
  val CorrelationIdHeaderName = "CorrelationId"
}
