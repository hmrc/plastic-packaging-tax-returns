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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json.obj
import play.api.libs.json._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.{Gettable, UserAnswers}

import java.time.{Instant, LocalDate}

class UserAnswersSpec extends PlaySpec {

  private val emptyUserAnswers = UserAnswers("id1", Json.obj(), Instant.ofEpochSecond(1))
  private val someUserAnswers = UserAnswers("id1", Json.obj(
    "fish" -> Json.obj("cakes" -> "cheese")
  ), Instant.ofEpochSecond(1))
  
  private class GetSomeFishCakes extends Gettable[String] {
    override def path: JsPath = JsPath \ "fish" \ "cakes"
  }

  "UserAnswers" should {
    
    "retrieve an answer" when {
      "calling with a JsPath" in {
        someUserAnswers.getOrFail[String](JsPath \ "fish" \ "cakes") mustBe "cheese"
      }
      "calling with a Gettable" in {
        someUserAnswers.getOrFail(new GetSomeFishCakes) mustBe "cheese"
      }
    }
    
    "retrieve an optional answer" when {
      "calling with a JsPath" in {
        someUserAnswers.get[String](JsPath \ "fish" \ "cakes") mustBe Some("cheese")
      }
      "calling with a Gettable" in {
        someUserAnswers.get(new GetSomeFishCakes) mustBe Some("cheese")
      }
    }
    
    "retrieve a missing optional answer" when {
      "calling with a JsPath" in {
        emptyUserAnswers.get[String](JsPath \ "fish" \ "cakes") mustBe None
      }
      "calling with a Gettable" in {
        emptyUserAnswers.get(new GetSomeFishCakes) mustBe None
      }
    }
    
    "complain if an answer is not there" when {
      "calling with a JsPath" in {
        the[Exception] thrownBy emptyUserAnswers.getOrFail[JsValue](JsPath \ "fish" \ "cakes") must have message
          "/fish/cakes is missing from user answers"
      }
      "calling with a Gettable" in {
        the[Exception] thrownBy emptyUserAnswers.getOrFail(new GetSomeFishCakes) must have message
          "/fish/cakes is missing from user answers"
      }
    }
    
    "complain if an answer cannot be converted to the required type" when {
      
      class GetWhiteboard extends Gettable[LocalDate] {
        override def path: JsPath = JsPath \ "fish" \ "cakes"
      }
      
      "calling with a JsPath" in {
        the[Exception] thrownBy someUserAnswers.getOrFail[LocalDate](JsPath \ "fish" \ "cakes") must have message
          "/fish/cakes in user answers cannot be read as type java.time.LocalDate"
      }
      "calling with a Gettable" in {
        the[Exception] thrownBy someUserAnswers.getOrFail(new GetWhiteboard) must have message
          "/fish/cakes in user answers cannot be read as type java.time.LocalDate"
      }
    }
    
    "quickly set lots of fields" when {
      
      "one key-value pair" in {
        someUserAnswers.setAll("x" -> "y") mustBe UserAnswers("id1", Json.obj(
          "fish" -> Json.obj("cakes" -> "cheese"),
          "x" -> "y"
        ), Instant.ofEpochSecond(1))
      }
      
      "multiple key-values" in {
        someUserAnswers.setAll("x" -> "y", "left" -> "right") mustBe UserAnswers("id1", Json.obj(
          "fish" -> Json.obj("cakes" -> "cheese"),
          "left" -> "right", 
          "x" -> "y", 
        ), Instant.ofEpochSecond(1))
      }
      
      "values are of multiple js types" in {
        someUserAnswers.setAll("x" -> JsNumber(1), "y" -> JsString("z")) mustBe UserAnswers("id1", Json.obj(
          "fish" -> Json.obj("cakes" -> "cheese"),
          "x" -> 1,  
          "y" -> "z", 
        ), Instant.ofEpochSecond(1))
      }
      
      "nested field" in {
        someUserAnswers.setAll("x" -> obj { "y" -> "z" }) mustBe UserAnswers("id1", Json.obj(
          "fish" -> Json.obj("cakes" -> "cheese"),
          "x" -> Json.obj {"y" -> "z"}
        ), Instant.ofEpochSecond(1))
      }
      
    }
    
  }
}
