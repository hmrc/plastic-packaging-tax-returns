import akka.http.scaladsl.model.headers.CacheDirectives.public
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, JsString, JsValue, Json, OFormat, Reads, Writes}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationDetail

import scala.util.{Failure, Success, Try}


final case class ObligationEmptyDataResponse private(code: String, reason: String)

object ObligationEmptyDataResponse {
  private def apply(code: String, reason: String) = {
    new ObligationEmptyDataResponse("NOT_FOUND", "What eber")
  }

  def apply() = {
    new ObligationEmptyDataResponse("NOT_FOUND", "What eber")
  }

  implicit val format: OFormat[ObligationEmptyDataResponse] =
    Json.format[ObligationEmptyDataResponse]
//  implicit val jsonReads: Reads[EmptyDataSchema] = (
//    (JsPath \ "code").read[String] and
//      (JsPath \ "reason").read[String]
//  )(EmptyDataSchema.apply _)

}

val t = ObligationEmptyDataResponse()
t.code
val str = """response body: {"code": "test", "reason": "hjkhk"} test"""
//Json.fromJson[EmptyDataSchema](str)

//Json.parse(str.toString()).as[EmptyDataSchema]

val r = str.substring(str.indexOf("{"), str.lastIndexOf("}") + 1)

val o = """{"code": "test"}"""

Try(Json.parse("{}").as[ObligationEmptyDataResponse]) match {
  case Success(i) => OK
  case Failure(f) => throw f
}



