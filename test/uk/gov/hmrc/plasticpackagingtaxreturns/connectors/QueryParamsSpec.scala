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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors

import java.time.LocalDate

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpec

class QueryParamsSpec extends AnyWordSpec {

  "QueryParams" should {
    "create query params with optional data" in {

      val params = QueryParams.fromOptions("p1" -> Some("value1"),
                                           "p2" -> Some(true),
                                           "p3" -> None,
                                           "p4" -> Some(DateFormat.isoFormat(LocalDate.of(2020, 11, 26)))
      )

      params mustBe Seq("p1" -> "value1", "p2" -> "true", "p4" -> "2020-11-26")

    }
  }
}
