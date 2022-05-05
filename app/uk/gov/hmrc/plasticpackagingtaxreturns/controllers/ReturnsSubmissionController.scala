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
import play.api.Logger
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.ReturnsConnector
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.ReturnsSubmissionRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class ReturnsSubmissionController @Inject() (
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  override val controllerComponents: ControllerComponents,
  returnsConnector: ReturnsConnector,
  appConfig: AppConfig
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  private val logger = Logger(this.getClass)

  def submit(pptReference: String): Action[TaxReturn] = doSubmission(pptReference, None)

  def amend(pptReference: String, submissionId: String): Action[TaxReturn] = doSubmission(pptReference, Some(submissionId))

  def get(pptReference: String, periodKey: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      returnsConnector.get(pptReference = pptReference, periodKey = periodKey).map {
        case Right(response)       => Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }

    }

  private def doSubmission(pptReference: String, submissionId: Option[String]): Action[TaxReturn] =
    authenticator.authorisedAction(authenticator.parsingJson[TaxReturn], pptReference) { implicit request =>
      returnsConnector.submitReturn(pptReference,
        ReturnsSubmissionRequest(request.body, appConfig.taxRatePoundsPerKg, submissionId = submissionId)
      ).map {
        case Right(response)       =>
          sessionRepository.clear(request.internalId).andThen {
            case Success(_)  => logger.info(s"Successfully removed tax return for $pptReference from cache")
            case Failure(ex) => logger.warn(s"Failed to remove tax return for $pptReference from cache- ${ex.getMessage}", ex)
          }
          Ok(response)
        case Left(errorStatusCode) => new Status(errorStatusCode)
      }
    }

}
