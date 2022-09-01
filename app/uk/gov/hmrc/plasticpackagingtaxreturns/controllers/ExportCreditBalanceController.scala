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

import play.api.libs.json.{JsString, JsValue, Json, OWrites, Writes}
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ExportCreditBalanceConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.ExportCreditBalanceController.CreditsCalculationResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.Obligation
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ObligationGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ExportCreditBalanceController @Inject() (
  exportCreditBalanceConnector: ExportCreditBalanceConnector,
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      {for { //todo credit service? is this too busy?
        userAnswers <- sessionRepository.get(request.cacheKey)
        obligation = getObligation(userAnswers.getOrElse(throw new IllegalStateException("UserAnswers is empty")))
        fromDate = obligation.fromDate.minusYears(2)
        toDate = obligation.fromDate.minusDays(1)
        creditsEither <- exportCreditBalanceConnector.getBalance(request.pptId, fromDate, toDate, request.internalId)
        availableCredit = creditsEither.fold(e => throw new Exception(s"Error calling EIS export credit, status: $e"), _.totalExportCreditAvailable)
        requestedCredit = BigDecimal(20)//todo creditsService.calculateRequestedCredit(UserAnswers) when available
      } yield {
        Ok(Json.toJson(CreditsCalculationResponse(availableCredit, requestedCredit)))
      }}.recover{
        case e: Exception => InternalServerError(Json.obj("message" -> JsString(e.getMessage)))
      }
    }

  private def getObligation(userAnswers: UserAnswers) = userAnswers.get[Obligation](ObligationGettable).getOrElse(
    throw new IllegalStateException("Obligation not found in user-answers")
  )

}

object ExportCreditBalanceController {

  //todo move!
  //canBeClaimed should have tests
  //writes should have tests
  final case class CreditsCalculationResponse(
                                         availableCreditInPounds: BigDecimal,
                                         totalRequestedCreditInPounds: BigDecimal
                                       ){
    val canBeClaimed: Boolean = totalRequestedCreditInPounds <= totalRequestedCreditInPounds
  }

  object CreditsCalculationResponse {

    implicit val writes: Writes[CreditsCalculationResponse] = new Writes[CreditsCalculationResponse] {
      def writes(calc: CreditsCalculationResponse): JsValue = {
        Json.obj(
          "availableCreditInPounds" -> calc.availableCreditInPounds,
          "totalRequestedCreditInPounds" -> calc.totalRequestedCreditInPounds,
          "canBeClaimed" -> calc.canBeClaimed
        )
      }
    }

  }
}
