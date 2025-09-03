/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{
  AvailableCreditService,
  CreditsCalculationService,
  UserAnswersService
}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExportCreditBalanceController @Inject() (
  authenticator: Authenticator,
  creditsCalculationService: CreditsCalculationService,
  availableCreditService: AvailableCreditService,
  override val controllerComponents: ControllerComponents,
  userAnswersService: UserAnswersService
)(implicit executionContext: ExecutionContext)
    extends BaseController {

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      userAnswersService.get(request.cacheKey)(getCreditClaim)
    }

  private def getCreditClaim(userAnswers: UserAnswers)(implicit request: AuthorizedRequest[_]): Future[Result] =
    for {
      availableCredit <- availableCreditService.getBalance(userAnswers)
      creditClaim = creditsCalculationService.totalRequestedCredit(userAnswers, availableCredit)
    } yield creditClaim match {
      case Some(value) => Ok(Json.toJson(value))
      case None        => NotFound(Json.obj("error" -> "Credit claim is not available"))
    }

}
