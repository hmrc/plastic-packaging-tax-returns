/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.{ExportCreditBalanceConnector, ObligationsDataConnector}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PPTObligationsController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  obligationsDataConnector: ObligationsDataConnector,
  exportCreditBalanceConnector: ExportCreditBalanceConnector,
  obligationsService: PPTObligationsService,
  appConfig: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) with Logging {

  private val internalServerError = InternalServerError("{}")

  //will trigger calls to EIS in prod so we can see easily if/when this is working
  def probeExportCreditBalance(pptReference: String)(implicit request: AuthorizedRequest[_]): Unit = {
    exportCreditBalanceConnector
      .getBalance(pptReference, LocalDate.now().minusYears(2), LocalDate.now().minusDays(1), request.internalId)
      .map(_ => logger.info("Export Credit API call was: successful"))
      .recover {
        case _ => logger.info("Export Credit API call was: unsuccessful")
      }
  }

  // todo dedupe
  def getOpen(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        probeExportCreditBalance(pptReference) //only for testing credits error. delete me soon 30/9/2022
        obligationsDataConnector.get(pptReference, request.internalId, None, None, Some(ObligationStatus.OPEN)).map {
          case Left(404)                     => NotFound("{}")
          case Left(_)                       => internalServerError
          case Right(obligationDataResponse) => createOpenResponse(obligationDataResponse)
        }
    }

  val pptStartDate: Option[LocalDate] = Some(LocalDate.of(2022, 4, 1))

  def getFulfilled(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, request.internalId, pptStartDate, Some(fulfilledToDate), Some(ObligationStatus.FULFILLED)).map {
          case Left(404)                     => NotFound("{}")
          case Left(_)                       => internalServerError
          case Right(obligationDataResponse) => createFulfilledResponse(obligationDataResponse)
        }
    }

  private def fulfilledToDate: LocalDate = {
    // todo see what this is about and still needed
    // If feature flag used by E2E test threads is set, then include test obligations that are in the future
    val today = LocalDate.now()
    if (appConfig.qaTestingInProgress)
      today.plusYears(1)
    else
      today
  }

  def createFulfilledResponse(obligationDataResponse: ObligationDataResponse) =
    obligationsService.constructPPTFulfilled(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        InternalServerError("{}")
      case Right(response) =>
        Ok(Json.toJson(response))
    }

  def createOpenResponse(obligationDataResponse: ObligationDataResponse): Result =
    obligationsService.constructPPTObligations(obligationDataResponse) match {
      case Left(error) =>
        logger.error(s"Error constructing Obligation response: $error.")
        InternalServerError("{}")
      case Right(response) =>
        Ok(Json.toJson(response))
    }

}
