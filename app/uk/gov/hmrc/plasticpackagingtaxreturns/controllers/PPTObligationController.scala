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

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTObligationController.PPTTaxStartDate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PPTObligationController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  obligationDataConnector: ObligationDataConnector,
  obligationsService: PPTObligationsService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) {

  private val logger = Logger(this.getClass)

  def get(ref: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default) {
      implicit request =>
        obligationDataConnector.get(ref, PPTTaxStartDate, LocalDate.now(), ObligationStatus.OPEN).map {
          case Left(errorStatusCode) =>
            logger.error(s"Error getting Obligation data from DES: $errorStatusCode.")
            InternalServerError("{}")
          case Right(obligationDataResponse) =>
            obligationsService.constructPPTObligations(obligationDataResponse) match {
              case Left(error) =>
                logger.error(s"Error constructing Obligation response: $error.")
                InternalServerError("{}")
              case Right(response) =>
                logger.info("Success: returning Obligation response.")
                Ok(Json.toJson(response))
            }
        }
    }

}

object PPTObligationController {
  private val PPTTaxStartDate = LocalDate.of(2022, 4, 1)
}