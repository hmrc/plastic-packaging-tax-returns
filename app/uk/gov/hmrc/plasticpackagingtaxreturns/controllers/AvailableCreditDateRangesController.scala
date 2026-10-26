/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.gettables.returns.ReturnObligationToDateGettable
import uk.gov.hmrc.plasticpackagingtaxreturns.services.{AvailableCreditDateRangesService, SubscriptionService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AvailableCreditDateRangesController @Inject() (
  availableCreditDateRangesService: AvailableCreditDateRangesService,
  authenticator: Authenticator,
  userAnswersService: UserAnswersService,
  override val controllerComponents: ControllerComponents,
  subscriptionsService: SubscriptionService
)(implicit val ec: ExecutionContext)
    extends BackendController(controllerComponents) with Logging {

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      userAnswersService.get(request.cacheKey) { (userAnswers: UserAnswers) =>
        getDataRange(userAnswers, pptReference)
      }
    }

  private def getDataRange(userAnswers: UserAnswers, pptReference: String)(implicit hc: HeaderCarrier): Future[Result] =
    subscriptionsService.getSubscriptionFuture(pptReference).map { subscription =>
      val taxStartDate  = subscription.taxStartDate()
      val returnEndDate = userAnswers.getOrFail(ReturnObligationToDateGettable)
      val dateRanges    = availableCreditDateRangesService.calculate(returnEndDate, taxStartDate)
      logger.warn(
        s"Logging data for DCA-271 Date Range minBy issue for pptReference: $pptReference\n" +
          s"subscription from subscriptionsService.getSubscriptionFuture(pptReference): $subscription \n" +
          s"subscription.taxStartDate(): $taxStartDate \n" +
          s"userAnswers.getOrFail(ReturnObligationToDateGettable): $returnEndDate \n" +
          s"uavailableCreditDateRangesService.calculate(returnEndDate, taxStartDate): $dateRanges \n" +
          s"json conversion of dateRanges: ${Json.toJson(dateRanges)}"
      )
      Ok(Json.toJson(dateRanges))
    }

}
