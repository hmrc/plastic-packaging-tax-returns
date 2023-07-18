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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsResultException, Json, OFormat}

import scala.util.Success

class EisHttpResponseSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar {

  private case class Example(test1: Int, test2: String)
  private implicit val exampleFormat: OFormat[Example] = Json.format[Example]

  ".json" should {
    
    "parse json body" in {
      EisHttpResponse(200, """{"a": "b"}""", correlationId = "").json mustBe Json.obj("a" -> "b") 
    }
    
    "handle not json" in {
      EisHttpResponse(200, "<html />", correlationId = "").json mustBe JsNull
    }

    "handle empty body" in {
      EisHttpResponse(200, body = "", correlationId = "").json mustBe JsNull
    }
    
  }
  
  ".jsonAs" should {

    "parse json body as given type" in {
      EisHttpResponse(200, """{"test1":1,"test2":"2"}""", correlationId = "")
        .jsonAs[Example].success mustBe Success(Example(1, "2"))
    }
    
    "handle json, but the wrong type" in {
      val triedExample = EisHttpResponse(200, """{"another":"thing"}""", correlationId = "")
        .jsonAs[Example]
      triedExample.failure.exception must have message "Response body could not be read as type EisHttpResponseSpec.this.Example" 
      triedExample.failure.exception mustBe a[RuntimeException]
      triedExample.failure.exception.getCause mustBe a[JsResultException]
    }
    
    "handle not json" ignore {
      val triedExample = EisHttpResponse(200, "<html />", correlationId = "")
        .jsonAs[Example]
      triedExample.failure.exception must have message "Response body is not json"
    }
    
  }
}
