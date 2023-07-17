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

import play.api.Logging
import play.api.http.Status
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.{JsDefined, JsString}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.audit.returns.GetObligations
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDataResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildDesHeader
import uk.gov.hmrc.plasticpackagingtaxreturns.util.{EisHttpClient, EisHttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ObligationsDataConnector @Inject()
(
  eisHttpClient: EisHttpClient,
  appConfig: AppConfig,
  auditConnector: AuditConnector,
)(implicit ec: ExecutionContext) extends Logging {

  val SUCCESS: String     = "Success"
  val FAILURE: String     = "Failure"

  def get(pptReference: String, internalId: String, fromDate: Option[LocalDate], toDate: Option[LocalDate], 
    status: Option[ObligationStatus]) (implicit hc: HeaderCarrier): Future[Either[Int, ObligationDataResponse]] = {


    val timerName               = "ppt.get.obligation.data.timer"
    val obligationStatus    = status.map(x => x.toString).getOrElse("")

    val queryParams =
      Seq(
        fromDate.map("from" -> DateFormat.isoFormat(_)),
        toDate.map("to" -> DateFormat.isoFormat(_)),
        status.map("status" -> _.toString))
        .flatten
        
    def successFun(response: EisHttpResponse): Boolean = response.status match {
      case Status.OK => true
      case Status.NOT_FOUND if response.json \ "code" == JsDefined(JsString("NOT_FOUND")) => true
      case _ => false
    }

    eisHttpClient.get(
      appConfig.enterpriseObligationDataUrl(pptReference),
      queryParams = queryParams,
      timerName,
      buildDesHeader,
      successFun
    ).map { response =>
        response.status match {
          case Status.OK => handleSuccess(pptReference, internalId, obligationStatus, queryParams, response)
          case Status.NOT_FOUND if response.isMagic404 =>
            val msg =  s"""Success on retrieving enterprise obligation data correlationId [${response.correlationId}] and """ +
            s"pptReference [$pptReference], params [$queryParams], status: ${Status.NOT_FOUND}, body: ${ObligationDataResponse.empty}"

            auditConnector.sendExplicitAudit(GetObligations.eventType,
              GetObligations(obligationStatus, internalId, pptReference, SUCCESS, Some(ObligationDataResponse.empty), Some(msg)))

            Right(ObligationDataResponse.empty)

          case _ => handleErrorResponse(pptReference, internalId, obligationStatus, queryParams, response)
        }
      }
  }

  private def handleSuccess
  (
    pptReference: String,
    internalId: String,
    obligationStatus: String,
    queryParams: Seq[(String, String)],
    response: EisHttpResponse
  )(implicit hc: HeaderCarrier) = {
    logger.info(s"Success on getting enterprise obligation data with correlationId [${response.correlationId}] pptReference [$pptReference] params [$queryParams]")
    val triedObligation = response.jsonAs[ObligationDataResponse]

    triedObligation match {
      case Success(obligation) =>
        auditConnector.sendExplicitAudit(GetObligations.eventType,
          GetObligations(obligationStatus, internalId, pptReference, SUCCESS, Some(obligation), None))

        Right(obligation)
      case Failure(error) =>

        auditConnector.sendExplicitAudit(GetObligations.eventType,
          GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(error.getMessage)))

        Left(INTERNAL_SERVER_ERROR)
    }
  }

  private def handleErrorResponse
  (
    pptReference: String,
    internalId: String,
    obligationStatus: String,
    queryParams: Seq[(String, String)],
    response: EisHttpResponse
  )(implicit hc: HeaderCarrier) = {
    val msg =  s"""Error returned when getting enterprise obligation data correlationId [${response.correlationId}] and """ +
      s"pptReference [$pptReference], params [$queryParams], status: ${response.status}"

    logger.error(msg)

    auditConnector.sendExplicitAudit(GetObligations.eventType,
      GetObligations(obligationStatus, internalId, pptReference, FAILURE, None, Some(s"$msg, body: ${response.json.toString()}")))

    Left(response.status)
  }

}

