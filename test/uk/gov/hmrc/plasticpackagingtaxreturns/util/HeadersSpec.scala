/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.MockitoSugar.{mock, reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.Headers.buildEisHeader

class HeadersSpec extends PlaySpec with BeforeAndAfterEach {

  private val appConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(appConfig)
    when(appConfig.eisEnvironment).thenReturn("eis")
  }

  "buildEisHeader" should {
    "build a header for EIS" in {
      when(appConfig.bearerToken).thenReturn("EIS_TOKEN")

      buildEisHeader("123", appConfig) mustBe Seq(
        "Environment"   -> "eis",
        "Accept"        -> "application/json",
        "CorrelationId" -> "123",
        "Authorization" -> "EIS_TOKEN"
      )
    }
  }

  "buildDesHeader" should {
    "build a header for EIS" in {
      when(appConfig.bearerToken).thenReturn("DES_TOKEN")

      buildEisHeader("123", appConfig) mustBe Seq(
        "Environment"   -> "eis",
        "Accept"        -> "application/json",
        "CorrelationId" -> "123",
        "Authorization" -> "DES_TOKEN"
      )
    }
  }

}
