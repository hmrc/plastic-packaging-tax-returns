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
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.Calculations
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.ReturnValues
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.PPTReturnsCalculatorService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class CalculationsController @Inject()(
                                        authenticator: Authenticator,
                                        sessionRepository: SessionRepository,
                                        override val controllerComponents: ControllerComponents,
                                        calculationsService: PPTReturnsCalculatorService
                                      )(implicit executionContext: ExecutionContext)
  extends BackendController(controllerComponents) with JSONResponses with Logging {

  def calculate(pptReference: String): Action[AnyContent] = {
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>

      sessionRepository.get(request.cacheKey).map {
        _.flatMap(ReturnValues(_)).fold(UnprocessableEntity("No user answers found")) { returnValues =>
          val calculations: Calculations = calculationsService.calculate(returnValues)

          if (calculations.isSubmittable) {
            Ok(Json.toJson(calculationsService.calculate(returnValues)))
          } else {
            UnprocessableEntity("The calculation is not submittable")
          }

        }
      }
    }
  }

}
