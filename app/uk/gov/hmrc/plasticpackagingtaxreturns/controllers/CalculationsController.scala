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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.amends.ReturnDisplayApiGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.{AmendsCalculations, Calculations}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditService, CreditsCalculationService, PPTCalculationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalculationsController @Inject()(
                                        authenticator: Authenticator,
                                        sessionRepository: SessionRepository,
                                        override val controllerComponents: ControllerComponents,
                                        calculationsService: PPTCalculationService,
                                        creditsService: CreditsCalculationService,
                                        availableCreditService: AvailableCreditService
                                      )(implicit executionContext: ExecutionContext)
  extends BackendBaseController with Logging {

  def calculateAmends(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) {
      implicit request =>
        sessionRepository.get(request.cacheKey).map { optUa => {
          val userAnswers = optUa.get
          val amend = AmendReturnValues(userAnswers).get
          val originalCalc = Calculations.fromReturn(userAnswers.getOrFail(ReturnDisplayApiGettable))
          val amendCalc = calculationsService.calculate(amend)
          Ok(Json.toJson(AmendsCalculations(original = originalCalc, amend = amendCalc)))
        }}
    }

  def calculateSubmit(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      sessionRepository.get(request.cacheKey).flatMap(
        _.fold(Future.successful(UnprocessableEntity("No user answers found"))){ userAnswers =>
          availableCreditService.getBalance(userAnswers).map{ availableCredit =>
            val requestedCredits = creditsService.totalRequestedCredit(userAnswers)
            NewReturnValues(requestedCredits, availableCredit)(userAnswers)
              .fold(UnprocessableEntity("User answers insufficient")) { returnValues =>
                val calculations: Calculations = calculationsService.calculate(returnValues)
                Ok(Json.toJson(calculations))
              }
          }
        }
      )
    }

}
