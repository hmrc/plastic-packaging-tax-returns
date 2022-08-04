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
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.calculations.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.{AmendReturnValues, NewReturnValues, ReturnValues}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTCalculationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class CalculationsController @Inject()(
                                        authenticator: Authenticator,
                                        sessionRepository: SessionRepository,
                                        override val controllerComponents: ControllerComponents,
                                        calculationsService: PPTCalculationService
                                      )(implicit executionContext: ExecutionContext)
  extends BackendController(controllerComponents) with JSONResponses with Logging {

  def calculateAmends(pptReference: String): Action[AnyContent] = calculate(pptReference, AmendReturnValues.apply)

  def calculateSubmit(pptReference: String): Action[AnyContent] = calculate(pptReference, NewReturnValues.apply)

  private def calculate(pptReference: String, returnValues: UserAnswers => Option[ReturnValues]): Action[AnyContent] = {
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>

      sessionRepository.get(request.cacheKey).map {
        _.flatMap(returnValues(_)).fold(UnprocessableEntity("No user answers found")) { returnValues =>
          val calculations: Calculations = calculationsService.calculate(returnValues)
          Ok(Json.toJson(calculations))
        }
      }
    }
  }

}