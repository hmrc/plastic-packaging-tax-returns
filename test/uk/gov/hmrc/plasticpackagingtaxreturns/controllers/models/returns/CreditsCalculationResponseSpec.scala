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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.models.returns

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsFalse, JsNumber, JsObject, Json}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditsCalculationResponse

class CreditsCalculationResponseSpec extends PlaySpec {

  "canClaim" must {
    "return true" when {
      "requested is smaller than available" in {
        CreditsCalculationResponse(
          availableCreditInPounds = 2,
          totalRequestedCreditInPounds = 1,
          totalRequestedCreditInKilograms = 0
        ).canBeClaimed mustBe true
      }
      "requested is equal to available" in {
        CreditsCalculationResponse(
          availableCreditInPounds = 2,
          totalRequestedCreditInPounds = 2,
          totalRequestedCreditInKilograms = 0
        ).canBeClaimed mustBe true
      }
    }

    "return false" when {
      "requested is larger than available" in {
        CreditsCalculationResponse(
          availableCreditInPounds = 1,
          totalRequestedCreditInPounds = 2,
          totalRequestedCreditInKilograms = 0
        ).canBeClaimed mustBe false
      }
    }
  }

  "json Writes" must {
    val obj = CreditsCalculationResponse(1 , 2, 3)
    val jsObject = Json.toJson(obj).as[JsObject]

    "contain the availableCreditInPounds field" in {
      jsObject.apply("availableCreditInPounds") mustBe JsNumber(1)
    }

    "contain the totalRequestedCreditInPounds field" in {
      jsObject.apply("totalRequestedCreditInPounds") mustBe JsNumber(2)
    }

    "contain the totalRequestedCreditInKilograms field" in {
      jsObject.apply("totalRequestedCreditInKilograms") mustBe JsNumber(3)
    }

    "contain the canBeClaimed field" in {
      jsObject.apply("canBeClaimed") mustBe JsFalse
    }
  }

}
