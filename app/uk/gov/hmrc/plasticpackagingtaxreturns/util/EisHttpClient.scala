package uk.gov.hmrc.plasticpackagingtaxreturns.util

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class HttpResponse(status: Int, body: String)

object HttpResponse {
  def fromHttpResponse(httpResponse: HmrcResponse): HttpResponse = {
    HttpResponse(httpResponse.status, httpResponse.body)
  }
}

/** Make a rest request-response call to an EIS endpoint or similar. Avoids exceptions for 4xx, 5xx responses. 
 * @param hmrcClient - underlying hmrc http client to use 
 */
class EisHttpClient @Inject() (hmrcClient: HmrcClient) {
  
  def put(url: String, requestBody: JsValue) (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    hmrcClient.PUT[JsValue, HmrcResponse](url, requestBody, Seq("header" -> "heed"))
      .map(HttpResponse.fromHttpResponse)
  }

}
