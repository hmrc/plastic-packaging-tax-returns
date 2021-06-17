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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends AnyWordSpec with Matchers with MockitoSugar {

  private val validAppConfig: Config =
    ConfigFactory.parseString("""
        |mongodb.uri="mongodb://localhost:27017/plastic-packaging-tax-returns"
        |mongodb.timeToLiveInSeconds=100
        |microservice.services.auth.host=localhostauth
        |microservice.services.auth.port=9988
        |microservice.metrics.graphite.host=graphite
        |auditing.enabled=true
        |eis.environment=ist0
    """.stripMargin)

  private val validServicesConfiguration = Configuration(validAppConfig)

  private def appConfig(conf: Configuration) = new AppConfig(conf, servicesConfig(conf))

  private def servicesConfig(conf: Configuration) = new ServicesConfig(conf)

  "AppConfig" should {
    "return config as object model when configuration is valid" in {
      val configService: AppConfig = appConfig(validServicesConfiguration)

      configService.authBaseUrl mustBe "http://localhostauth:9988"
      configService.auditingEnabled mustBe true
      configService.graphiteHost mustBe "graphite"
      configService.dbTimeToLiveInSeconds mustBe 100
    }
  }
}
