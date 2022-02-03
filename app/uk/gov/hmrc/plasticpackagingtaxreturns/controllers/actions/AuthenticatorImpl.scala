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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthAction.{
  pptEnrolmentIdentifierName,
  pptEnrolmentKey
}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthenticatorImpl @Inject()
(override val authConnector: AuthConnector, cc: ControllerComponents)
(implicit ec: ExecutionContext)
  extends BackendController(cc) with AuthorisedFunctions with Authenticator {

  private val logger = Logger(this.getClass)

  def parsingJson[T](implicit rds: Reads[T]): BodyParser[T] =
    parse.json.validate { json =>
      json.validate[T] match {
        case JsSuccess(value, _) => Right(value)
        case JsError(error) =>
          val errorResponse = Json.toJson(ErrorResponse(BAD_REQUEST, "Bad Request"))
          logger.warn(s"Bad Request [$errorResponse]")
          logger.warn(s"Errors: [$error]")
          Left(BadRequest(errorResponse))
      }
    }

  def authorisedAction[A](bodyParser: BodyParser[A])(body: AuthorizedRequest[A] => Future[Result]): Action[A] =
    Action.async(bodyParser) { implicit request =>
      authorisedWithPptId.flatMap {
        case Right(authorisedRequest) =>
          logger.info(s"Authorised request for ${authorisedRequest.pptId}")
          body(authorisedRequest)
        case Left(error) =>
          logger.error(s"Problems with Authorisation: ${error.message}")
          Future.successful(Unauthorized(error.message))
      }
    }

  def authorisedWithPptId[A](implicit
    hc: HeaderCarrier,
    request: Request[A]
  ): Future[Either[ErrorResponse, AuthorizedRequest[A]]] =
    authorised(Enrolment(pptEnrolmentKey)).retrieve(allEnrolments) { enrolments =>
      hasEnrolment(enrolments, pptEnrolmentIdentifierName) match {
        case Some(pptReference) => Future.successful(Right(AuthorizedRequest(pptReference.value, request)))
        case _ =>
          val msg = "Unauthorised access. User without PPT Id."
          logger.error(msg)
          Future.successful(Left(ErrorResponse(UNAUTHORIZED, msg)))
      }
    } recover {
      case error: AuthorisationException =>
        logger.error(s"Unauthorised Exception for ${request.uri} with error ${error.reason}")
        Left(ErrorResponse(UNAUTHORIZED, "Unauthorized for plastic packaging tax"))
      case ex: Throwable =>
        val msg = "Internal server error is " + ex.getMessage
        logger.error(msg)
        Left(ErrorResponse(INTERNAL_SERVER_ERROR, msg))
    }

  private def hasEnrolment(enrolmentsList: Enrolments, identifier: String): Option[EnrolmentIdentifier] =
    enrolmentsList.enrolments
      .filter(_.key == pptEnrolmentKey)
      .flatMap(_.identifiers)
      .find(_.key == identifier)

}

object AuthAction {
  val pptEnrolmentKey            = "HMRC-PPT-ORG"
  val pptEnrolmentIdentifierName = "EtmpRegistrationNumber"
}

case class AuthorizedRequest[A](pptId: String, request: Request[A]) extends WrappedRequest[A](request)
