package uk.gov.hmrc.plasticpackagingtaxreturns.util

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/** An http response that allows for equality and same-instance
 * @param status - http status code from response
 * @param body - response body as a [[String]]
 */
case class HttpResponse(status: Int, body: String)

object HttpResponse {
  /** Create from an hmrc [[HmrcResponse]]
   * @param hmrcResponse source
   * @return [[HttpResponse]]
   * @note does not keep a reference to [[HmrcResponse]]  
   */
  def fromHttpResponse(hmrcResponse: HmrcResponse): HttpResponse = {
    HttpResponse(hmrcResponse.status, hmrcResponse.body)
  }
}

/** Make a rest request-response call to an EIS endpoint or similar. Avoids exceptions for 4xx, 5xx responses. 
 * @note auto-rolled by injector
 * @param hmrcClient underlying hmrc http client to use 
 * @param appConfig source for required header field values 
 */
class EisHttpClient @Inject() (
  hmrcClient: HmrcClient,
  appConfig: AppConfig,
  edgeOfSystem: EdgeOfSystem
) {

  /**
   * @tparam HappyModel the type of the model payload / request body 
   * @param url full url of endpoint to send put request to
   * @param requestBody object to send in put-request body, must have an implicit json.Writes[A] in-scope
   * @param hc header carrier from up-stream request
   * @param ec current execution context
   * @return [[HttpResponse]]
   */
  def put[HappyModel](url: String, requestBody: HappyModel) (implicit hc: HeaderCarrier, ec: ExecutionContext, 
    writes: Writes[HappyModel]): Future[HttpResponse] = {
    
    val headers = Seq(
      "Environment" -> appConfig.eisEnvironment, 
      HeaderNames.ACCEPT -> MimeTypes.JSON, 
      HeaderNames.AUTHORIZATION -> appConfig.bearerToken,
      "CorrelationId" -> edgeOfSystem.createUuid.toString
    )
    
    hmrcClient.PUT[HappyModel, HmrcResponse](url, requestBody, headers)
      .map(HttpResponse.fromHttpResponse)
  }

}
