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

import play.api.libs.json.{JsString, Json}
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationToDateGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditsCalculationResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, TaxRateService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ExportCreditBalanceController @Inject() (
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  calculateService: CreditsCalculationService,
  creditService: AvailableCreditService,
  taxRateService: TaxRateService,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext) extends BaseController {

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>

      {for {
        userAnswersOpt <- sessionRepository.get(request.cacheKey)
        userAnswers = userAnswersOpt.getOrElse(throw new IllegalStateException("UserAnswers is empty"))
        availableCredit <- creditService.getBalance(userAnswers)
        requestedCredit = calculateService.totalRequestedCredit(userAnswers)
        taxRate = taxRateService.lookupTaxRateForPeriod(userAnswers.getOrFail(ReturnObligationToDateGettable))
      } yield
        Ok(Json.toJson(CreditsCalculationResponse(
          availableCredit,
          requestedCredit.moneyInPounds,
          requestedCredit.weight,
          taxRate
        )))
      }.recover{
        case e: Exception => InternalServerError(Json.obj("message" -> JsString(e.getMessage)))
      }
    }

}

