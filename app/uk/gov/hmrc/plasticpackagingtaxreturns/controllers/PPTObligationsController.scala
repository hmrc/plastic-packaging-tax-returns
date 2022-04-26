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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PPTObligationsController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  obligationsDataConnector: ObligationsDataConnector,
  obligationsService: PPTObligationsService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) with Logging{

  private val internalServerError = InternalServerError("{}")

  def getOpen(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, None, None, Some(ObligationStatus.OPEN)).map {
          case Left(_) =>
            internalServerError
          case Right(obligationDataResponse) =>
            obligationsService.constructPPTObligations(obligationDataResponse) match {
              case Left(error) =>
                logger.error(s"Error constructing Obligation response: $error.")
                internalServerError
              case Right(response) =>
                Ok(Json.toJson(response))
            }
        }
    }

  def getFulfilled(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, None, None, Some(ObligationStatus.FULFILLED)).map {
          case Left(_) =>
            internalServerError
          case Right(obligationDataResponse) =>
            obligationsService.constructPPTFulfilled(obligationDataResponse) match {
              case Left(error) =>
                logger.error(s"Error constructing Obligation response: $error.")
                internalServerError
              case Right(response) =>
                Ok(Json.toJson(response))
            }
        }
    }

}
