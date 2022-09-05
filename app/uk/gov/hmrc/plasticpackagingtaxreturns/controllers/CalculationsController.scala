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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{AmendsCalculations, Calculations}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, OriginalReturnForAmendValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{CreditsCalculationService, PPTCalculationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.ExecutionContext

@Singleton
class CalculationsController @Inject()(
                                        authenticator: Authenticator,
                                        sessionRepository: SessionRepository,
                                        override val controllerComponents: ControllerComponents,
                                        calculationsService: PPTCalculationService,
                                        creditsService: CreditsCalculationService
                                      )(implicit executionContext: ExecutionContext)
  extends BackendBaseController with Logging {

  def calculateAmends(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        sessionRepository.get(request.cacheKey).map { optUa => {
          for {
            userAnswers <- optUa
            original <- OriginalReturnForAmendValues(userAnswers)
            amend <- AmendReturnValues(userAnswers)
            originalCalc = calculationsService.calculate(original)
            amendCalc = calculationsService.calculate(amend)
          } yield
            Json.toJson(AmendsCalculations(original = originalCalc, amend = amendCalc))
        }.fold(UnprocessableEntity("No user answers found"))(Ok(_))
        }
    }

  def calculateSubmit(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      sessionRepository.get(request.cacheKey).map {
        _.flatMap{userAnswers =>
          val requestedCredits = creditsService.totalRequestCreditInPounds(userAnswers)
          NewReturnValues(requestedCredits)(userAnswers)
        }.fold(UnprocessableEntity("No user answers found")) { returnValues =>
          val calculations: Calculations = calculationsService.calculate(returnValues)
          Ok(Json.toJson(calculations))
        }
      }
    }

}
