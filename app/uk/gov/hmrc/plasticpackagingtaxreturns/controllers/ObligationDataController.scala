/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ObligationDataController @Inject() (
  obligationDataConnector: ObligationDataConnector,
  authenticator: Authenticator,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  def get(pptReference: String, fromDate: LocalDate, toDate: LocalDate, status: Option[String]): Action[AnyContent] =
    authenticator.authorisedAction(parse.default) { implicit request =>
      obligationDataConnector.get(pptReference, fromDate, toDate, ObligationStatus.withName(status.getOrElse(""))).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }
    }

}