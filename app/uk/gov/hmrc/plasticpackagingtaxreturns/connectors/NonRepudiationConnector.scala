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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import akka.actor.ActorSystem
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import play.api.http.Status.ACCEPTED
import play.api.libs.json.{JsObject, Json, Reads, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpReadsHttpResponse, HttpResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.NonRepudiationConnector._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{NonRepudiationMetadata, NonRepudiationSubmissionAccepted}
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Retry

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

@Singleton
class NonRepudiationConnector @Inject() (
  httpClient: HttpClient,
  val config: AppConfig,
  metrics: Metrics,
  override val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends HttpReadsHttpResponse with Retry {

  def submitNonRepudiation(encodedPayloadString: String, nonRepudiationMetadata: NonRepudiationMetadata)(implicit
    hc: HeaderCarrier
  ): Future[NonRepudiationSubmissionAccepted] = {
    val timer    = metrics.defaultRegistry.timer(TimerKey).time()
    val jsonBody = NrsSubmittable(encodedPayloadString, nonRepudiationMetadata).toJsObject

    retry[NonRepudiationSubmissionAccepted](config.nrsRetries: _*)(shouldRetry, _ => ReasonForRetry) {
      submit(timer, jsonBody)
    }
  }

  private def submit(timer: Timer.Context, jsonBody: JsObject)(implicit
    hc: HeaderCarrier
  ): Future[NonRepudiationSubmissionAccepted] =
    httpClient.POST[JsObject, HttpResponse](url = config.nonRepudiationSubmissionUrl,
                                            body = jsonBody,
                                            headers = Seq(XApiKeyHeaderKey -> config.nonRepudiationApiKey)
    ).andThen { case _ => timer.stop() }
      .map {
        response =>
          response.status match {
            case ACCEPTED =>
              val submissionId = response.json.as[NrsSubmission].nrSubmissionId
              NonRepudiationSubmissionAccepted(submissionId)
            case _ =>
              throw new HttpException(response.body, response.status)
          }
      }

  private def shouldRetry[A](response: Try[A]): Boolean =
    response match {
      case Failure(e) if e.asInstanceOf[HttpException].responseCode == 500 => true
      case _ => false
    }

}

object NonRepudiationConnector {

  private val TimerKey = "ppt.nrs.submission.timer"
  private val XApiKeyHeaderKey = "X-API-Key"
  private val ReasonForRetry = "Non Repudiation Service submission failed"

  private final case class NrsSubmittable(payload: String, metadata: NonRepudiationMetadata){
    def toJsObject: JsObject = Json.toJson(this).as[JsObject]
  }
  private final case class NrsSubmission(nrSubmissionId: String)

  private implicit val NrsSubmittableWrites: Writes[NrsSubmittable] = Json.writes[NrsSubmittable]
  private implicit val NrsSubmissionReads: Reads[NrsSubmission] = Json.reads[NrsSubmission]
}
