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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.query

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.mvc.QueryStringBindable

import scala.util.{Failure, Success, Try}

object QueryStringParams {

  implicit val dateQueryParamsBinder = new QueryStringBindable[LocalDate] {

    override def unbind(key: String, date: LocalDate): String = date.toString

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDate]] =
      for {
        dates <- params.get(key)
      } yield Try {
        LocalDate.parse(dates.head, DateTimeFormatter.ISO_LOCAL_DATE)
      } match {
        case Success(v) => Right(v)
        case Failure(_) => Left("ERROR_INVALID_DATE")
      }

  }

}
