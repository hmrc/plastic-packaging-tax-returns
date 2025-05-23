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

import play.api.Logger
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.Authenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.services.UserAnswersCleaner
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CacheController @Inject() (
  authenticator: Authenticator,
  sessionRepository: SessionRepository,
  userAnswersCleaner: UserAnswersCleaner,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

  private val logger = Logger(this.getClass)

  def get(pptReference: String): Action[AnyContent] =
    authenticator.authorisedAction(parse.default, pptReference) { implicit request =>
      sessionRepository.get(request.cacheKey).flatMap {
        case Some(ua) =>
          userAnswersCleaner.clean(ua, request.pptReference)
            .flatMap { tuple =>
              val (userAnswers, hasBeenCleaned) = tuple
              (if (hasBeenCleaned)
                 sessionRepository.set(userAnswers)
               else Future.successful(true)).map(_ => Ok(userAnswers))
            }
        case None => Future.successful(NotFound)
      }
    }

  def set(pptReference: String): Action[UserAnswers] =
    authenticator.authorisedAction(authenticator.parsingJson[UserAnswers], pptReference) { implicit request =>
      logPayload("Cache user answers", request.body)
      sessionRepository
        .set(request.body)
        .map(logPayload("Cache user answers response", _))
        .map(userAnswers => Created(userAnswers))
    }

  private def logPayload[T](prefix: String, payload: T)(implicit wts: Writes[T]): T = {
    logger.debug(s"$prefix, Payload: ${Json.toJson(payload)}")
    payload
  }

}
