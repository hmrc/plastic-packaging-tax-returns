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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.ReturnsSubmissionRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSubmissionController @Inject() (
  authenticator: Authenticator,
  taxReturnRepository: TaxReturnRepository,
  override val controllerComponents: ControllerComponents,
  returnsConnector: ReturnsConnector
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  def submit(returnId: String) =
    authenticator.authorisedAction(parse.default) { implicit request =>
      taxReturnRepository.findById(returnId).flatMap {
        case Some(taxReturn) =>
          returnsConnector.submitReturn(returnId, ReturnsSubmissionRequest.fromTaxReturn(taxReturn)).map {
            case Right(response)       => Ok(response)
            case Left(errorStatusCode) => new Status(errorStatusCode)
          }
        case None => Future.successful(NotFound)
      }
    }

  def get(returnId: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default) { implicit request =>
      returnsConnector.get(pptReference = returnId, periodKey = periodKey).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }
    }

}
