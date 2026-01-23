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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends AnyWordSpec with Matchers with MockitoSugar {

  private val validAppConfig =
    """
      |mongodb.uri="mongodb://localhost:27017/plastic-packaging-tax-returns"
      |mongodb.timeToLiveInSeconds=100
      |microservice.services.auth.host=localhostauth
      |microservice.services.auth.port=9988
      |microservice.metrics.graphite.host=graphite
      |microservice.services.eis.bearerToken=test123456
      |microservice.services.des.bearerToken=test123456
      |microservice.services.nrs.host=localhost
      |microservice.services.nrs.port=8506
      |microservice.services.nrs.api-key=test-key
      |auditing.enabled=true
      |eis.environment=ist0
      |nrs.retries=["1s", "2s", "4s"]
      |tax-rate.year.2022=0.20
      |tax-rate.year.2023=0.21082
      |tax-rate.year.2024=0.21785
      |tax-rate.year.2025=0.22369
      |tax-rate.year.2026=0.22882
      |    """.stripMargin

  private def createAppConfig(configString: String = "") = {
    val config        = ConfigFactory.parseString(validAppConfig + configString)
    val configuration = Configuration(config)
    new AppConfig(configuration, new ServicesConfig(configuration))
  }

  "AppConfig" should {

    "return config as object model when configuration is valid" in {
      val configService: AppConfig = createAppConfig()
      configService.authBaseUrl mustBe "http://localhostauth:9988"
      configService.auditingEnabled mustBe true
      configService.graphiteHost mustBe "graphite"
      configService.dbTimeToLiveInSeconds mustBe 100
    }

    "return override-system-date-time" when {

      "value is present" in {
        val configService = createAppConfig("""override-system-date-time = "whatEver" """)
        configService.overrideSystemDateTime mustBe Some("whatEver")
      }

      "value is missing" in {
        val configService = createAppConfig()
        configService.overrideSystemDateTime mustBe None
      }

      "content is boolean" in {
        val frontendAppConfig = createAppConfig("""override-system-date-time = false""")
        frontendAppConfig.overrideSystemDateTime mustBe Some("false")
      }

    }

  }
}
