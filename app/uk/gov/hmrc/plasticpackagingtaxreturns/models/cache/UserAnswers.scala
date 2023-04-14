/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.cache

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import scala.reflect.runtime.universe.typeOf
import scala.reflect.runtime.universe.TypeTag

final case class UserAnswers(
  id: String,
  data: JsObject = Json.obj(),
  lastUpdated: Instant = Instant.now
) {

  def getOrFail[A](gettable: Gettable[A])(implicit rds: Reads[A], tt: TypeTag[A]): A = 
    getOrFail(gettable.path)
  
  def getOrFail[A](path: JsPath) (implicit rds: Reads[A], tt: TypeTag[A]): A = {
    Reads
      .at(path)
      .reads(data)
      .recover {
        case JsError((_, JsonValidationError("error.path.missing" :: Nil) :: _) :: _) =>
          throw new IllegalStateException(s"$path is missing from user answers")
        case JsError((_, JsonValidationError(message :: Nil, _*) :: _) :: _) if message.startsWith("error.expected") =>
          throw new IllegalStateException(s"$path in user answers cannot be read as type ${typeOf[A]}")
      }
      .get
  }

  def get[A](gettable: Gettable[A])(implicit rds: Reads[A]): Option[A] = get(gettable.path)

  def get[A](path: JsPath)(implicit rds: Reads[A]): Option[A] =
    Reads
      .optionNoError(Reads.at(path))
      .reads(data)
      .getOrElse(None)
      
  def setAll[A](all: (String, A)*) (implicit writes: Writes[A]): UserAnswers = {
    copy(data = data ++ JsObject(all.map {
      case (x, y) => (x, Json.toJson(y))
    }))
  }
}

object UserAnswers {

  val reads: Reads[UserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[String] and
        (__ \ "data").read[JsObject] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
      )(UserAnswers.apply _)
  }

  val writes: OWrites[UserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[String] and
        (__ \ "data").write[JsObject] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
      )(unlift(UserAnswers.unapply))
  }

  implicit val format: OFormat[UserAnswers] = OFormat(reads, writes)
}

