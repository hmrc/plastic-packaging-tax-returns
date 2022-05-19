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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ObligationsDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTObligationsService
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
  appConfig: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) with Logging {

  private val internalServerError = InternalServerError("{}")

  // todo dedupe

  def getOpen(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, None, None, Some(ObligationStatus.OPEN)).map {
          case Left(404) => NotFound("{}")
          case Left(_)                        => internalServerError
          case Right(obligationDataResponse)  =>
            obligationsService.createOpenResponse(obligationDataResponse)
        }
    }

  val pptStartDate: Option[LocalDate] = Some(LocalDate.of(2022, 4, 1))

  def getFulfilled(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        obligationsDataConnector.get(pptReference, pptStartDate, Some(fulfilledToDate), Some(ObligationStatus.FULFILLED)).map {
          case Left(404)                      => NotFound("{}")
          case Left(_)                        => internalServerError
          case Right(obligationDataResponse)  => obligationsService.createFulfilledResponse(obligationDataResponse)
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


}
