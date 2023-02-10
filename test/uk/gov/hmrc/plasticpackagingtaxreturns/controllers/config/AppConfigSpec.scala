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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.TaxRate
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate

class AppConfigSpec extends AnyWordSpec with Matchers with MockitoSugar {

  private val validAppConfig: Config =
    ConfigFactory.parseString("""
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
        |taxRatePoundsPerKgYearly=[
        |   {taxRate:"0.20",date:"2022-04-01"}
        | ]
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

    "taxRatePoundsPerKgYearly" should {
      "get a list of sorted tax rate "in {
        val configService: AppConfig = appConfig(validServicesConfiguration)

        configService.taxRatePoundsPerKgYearly mustBe Seq(
          TaxRate(BigDecimal(0.2), LocalDate.of(2022, 4,1))
        )
      }
    }
  }
}
