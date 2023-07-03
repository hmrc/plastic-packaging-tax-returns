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

package uk.gov.hmrc.plasticpackagingtaxreturns.config

import play.api.Configuration
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  lazy val eisHost: String = servicesConfig.baseUrl("eis")
  lazy val desHost: String = servicesConfig.baseUrl("des")
  lazy val nrsHost: String = servicesConfig.baseUrl("nrs")

  def subscriptionDisplayUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"

  def subscriptionUpdateUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/subscriptions/PPT/$pptReference/update"

  def exportCreditBalanceDisplayUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/export-credits/PPT/$pptReference"

  def returnsSubmissionUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/returns/PPT/$pptReference"

  def returnsDisplayUrl(pptReference: String, periodKey: String): String =
    s"$eisHost/plastic-packaging-tax/returns/PPT/$pptReference/$periodKey"

  def enterpriseObligationDataUrl(pptReference: String): String =
    s"$desHost/enterprise/obligation-data/zppt/$pptReference/PPT"

  def enterpriseFinancialDataUrl(pptReference: String): String =
    s"$desHost/enterprise/financial-data/ZPPT/$pptReference/PPT"

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val auditingEnabled: Boolean   = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String       = config.get[String]("microservice.metrics.graphite.host")
  val dbTimeToLiveInSeconds: Int = config.get[Int]("mongodb.timeToLiveInSeconds")

  val eisEnvironment: String = config.get[String]("eis.environment")

  val bearerToken: String = s"Bearer ${config.get[String]("microservice.services.eis.bearerToken")}"

  lazy val nonRepudiationSubmissionUrl: String = s"$nrsHost/submission"

  lazy val nonRepudiationApiKey: String =
    servicesConfig.getString("microservice.services.nrs.api-key")

  val nrsRetries: Seq[FiniteDuration] = config.get[Seq[FiniteDuration]]("nrs.retries")

  val desBearerToken: String = s"Bearer ${config.get[String]("microservice.services.des.bearerToken")}"

  val errorLogAlertTag = "PPT_ERROR_RAISE_ALERT"

  /** Override the current system data-time, for coding and testing, or set to false to use system date-time. The
   * system date-time is also used if the config value is missing or its value fails to parse.
   * @return
   *   - [[None]] if no date-time override config value is present
   *   - Some[ [[String]] ] if an override config value is present, needs to be a ISO_LOCAL_DATE_TIME serialised
   *   date-time for override to work
   * @example {{{"2023-03-31T23:59:59"}}}
   * @example {{{"2023-04-01T00:00:00"}}}
   * @example {{{false}}}
   * @see [[DateTimeFormatter.ISO_LOCAL_DATE_TIME]]
   * @see [[EdgeOfSystem.localDateTimeNow]]
   */
  def overrideSystemDateTime: Option[String] =
    config.getOptional[String]("features.override-system-date-time")

}
