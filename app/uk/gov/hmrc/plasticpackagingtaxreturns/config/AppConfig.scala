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

package uk.gov.hmrc.plasticpackagingtaxreturns.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  lazy val eisHost: String = servicesConfig.baseUrl("eis")
  lazy val nrsHost: String = servicesConfig.baseUrl("nrs")

  def subscriptionDisplayUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/subscriptions/PPT/$pptReference/display"

  def subscriptionUpdateUrl(pptReference: String): String =
    s"$eisHost/plastic-packaging-tax/subscriptions/PPT/$pptReference/update"

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val auditingEnabled: Boolean   = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String       = config.get[String]("microservice.metrics.graphite.host")
  val dbTimeToLiveInSeconds: Int = config.get[Int]("mongodb.timeToLiveInSeconds")

  val eisEnvironment: String = config.get[String]("eis.environment")

  val bearerToken: String = s"Bearer ${config.get[String]("microservice.services.eis.bearerToken")}"

  lazy val nonRepudiationSubmissionUrl: String = s"${nrsHost}/submission"

  lazy val nonRepudiationApiKey: String =
    servicesConfig.getString("microservice.services.nrs.api-key")

  val nrsRetries: Seq[FiniteDuration] = config.get[Seq[FiniteDuration]]("nrs.retries")
}
