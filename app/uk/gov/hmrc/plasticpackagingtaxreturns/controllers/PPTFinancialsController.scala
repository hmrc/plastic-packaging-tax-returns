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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.PPTFinancialsController.PPTTaxStartDate
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTFinancialsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PPTFinancialsController @Inject() (
  cc: ControllerComponents,
  authenticator: Authenticator,
  financialDataConnector: FinancialDataConnector,
  financialsService: PPTFinancialsService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc) {

  def get(ref: String): Action[AnyContent] =
    Action.async {
      implicit request =>
        financialDataConnector.get(ref,
                                   PPTTaxStartDate,
                                   LocalDate.now(),
                                   onlyOpenItems = Some(true),
                                   includeLocks = Some(true),
                                   calculateAccruedInterest = Some(true),
                                   customerPaymentInformation = Some(true) //todo confirm all these params
        ).map {
          case Left(_) =>
            InternalServerError("{}")
          case Right(financialDataResponse) =>
            val financials = financialsService.construct(financialDataResponse)
            Ok(Json.toJson(financials))
        }
    }

}

object PPTFinancialsController {
  //todo to a higher scope, package object or app conf?
  private val PPTTaxStartDate = LocalDate.of(2022, 4, 1)
}
