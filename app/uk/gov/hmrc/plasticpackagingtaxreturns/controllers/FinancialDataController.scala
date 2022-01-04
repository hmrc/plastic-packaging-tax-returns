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

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class FinancialDataController @Inject() (
  financialDataConnector: FinancialDataConnector,
  authenticator: Authenticator,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  def get(
    pptReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    onlyOpenItems: Option[Boolean] = None,
    includeLocks: Option[Boolean] = None,
    calculateAccruedInterest: Option[Boolean] = None,
    customerPaymentInformation: Option[Boolean] = None
  ): Action[AnyContent] =
    authenticator.authorisedAction(parse.default) { implicit request =>
      financialDataConnector.get(pptReference,
                                 fromDate,
                                 toDate,
                                 onlyOpenItems,
                                 includeLocks,
                                 calculateAccruedInterest,
                                 customerPaymentInformation
      ).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }
    }

}
