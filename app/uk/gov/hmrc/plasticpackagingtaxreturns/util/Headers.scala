/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.http.{HeaderNames, MimeTypes}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

object Headers {

  val correlationIdHeaderName = "CorrelationId"
  val environmentHeaderName   = "Environment"

  def buildEisHeader(correlationId: String, appConfig: AppConfig): Seq[(String, String)] =
    headers(correlationId, appConfig) :+ HeaderNames.AUTHORIZATION -> appConfig.bearerToken

  def buildDesHeader(correlationId: String, appConfig: AppConfig): Seq[(String, String)] =
    headers(correlationId, appConfig) :+ HeaderNames.AUTHORIZATION -> appConfig.desBearerToken

  private def headers(correlationId: String, appConfig: AppConfig): Seq[(String, String)] =
    Seq(
      environmentHeaderName   -> appConfig.eisEnvironment,
      HeaderNames.ACCEPT      -> MimeTypes.JSON,
      correlationIdHeaderName -> correlationId
    )

}
