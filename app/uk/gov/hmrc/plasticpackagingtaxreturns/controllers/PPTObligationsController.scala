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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationDataResponse, ObligationStatus}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PPTObligationsController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  obligationsDataConnector: ObligationsDataConnector,
  obligationsService: PPTObligationsService,
  edgeOfSystem: EdgeOfSystem
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) with Logging {

  private val internalServerError = InternalServerError("{}")


  def getOpen(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
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
        obligationsDataConnector.get(pptReference, request.internalId, pptStartDate, Some(edgeOfSystem.today), Some(ObligationStatus.FULFILLED)).map {
          case Left(404)                     => NotFound("{}")
          case Left(_)                       => internalServerError
          case Right(obligationDataResponse) => createFulfilledResponse(obligationDataResponse)
        }
    }

  def createFulfilledResponse(obligationDataResponse: ObligationDataResponse): Result =
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
