/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, _}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.response.JSONResponses
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{TaxReturn, TaxReturnRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnsController @Inject() (
  taxReturnRepository: TaxReturnRepository,
  override val controllerComponents: ControllerComponents
)(implicit executionContext: ExecutionContext)
    extends BackendController(controllerComponents) with JSONResponses {

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

  def get(id: String): Action[AnyContent] =
    Action.async { implicit request =>
      taxReturnRepository.findById(id).map {
        case Some(taxReturn) => Ok(taxReturn)
        case None            => NotFound
      }
    }

  def create(): Action[TaxReturnRequest] =
    Action.async(parsingJson[TaxReturnRequest]) { implicit request =>
      logPayload("Create Tax Return Request Received", request.body)
      taxReturnRepository
        .create(request.body.toTaxReturn("123"))
        .map(logPayload("Create Tax Return Response", _))
        .map(taxReturn => Created(taxReturn))
    }

  def update(id: String): Action[TaxReturnRequest] =
    Action.async(parsingJson[TaxReturnRequest]) { implicit request =>
      logPayload("Update Tax Return Request Received", request.body)
      taxReturnRepository.findById(id).flatMap {
        case Some(_) =>
          taxReturnRepository
            .update(request.body.toTaxReturn(id))
            .map(logPayload("Update Tax Return Response", _))
            .map {
              case Some(taxReturn) => Ok[TaxReturn](taxReturn)
              case None            => NotFound
            }
        case None =>
          logPayload("Update Tax Return Response", "Not Found")
          Future.successful(NotFound)
      }
    }

  private def logPayload[T](prefix: String, payload: T)(implicit wts: Writes[T]): T = {
    logger.debug(s"$prefix, Payload: ${Json.toJson(payload)}")
    payload
  }

}
