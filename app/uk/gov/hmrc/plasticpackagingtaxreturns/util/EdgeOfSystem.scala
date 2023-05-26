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

package uk.gov.hmrc.plasticpackagingtaxreturns.util

import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig

import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import java.util.Base64
import javax.inject.Inject
import scala.util.Try

class EdgeOfSystem @Inject() (appConfig: AppConfig) {
  def getMessageDigestSingleton: MessageDigest = MessageDigest.getInstance("SHA-256")
  def createEncoder: Base64.Encoder = Base64.getEncoder

  /** The current system date-time, or the overridden date-time if set in config
   *
   * @return
   *  - current system date-time, if no override in-place
   *  - overridden date-time, if set
   * @see [[AppConfig.overrideSystemDateTime]]
   * @see [[DateTimeFormatter.ISO_LOCAL_DATE_TIME]]
   */
  def localDateTimeNow: LocalDateTime = {
    appConfig
      .overrideSystemDateTime
      .flatMap(parseDate)
      .getOrElse(LocalDateTime.now())
  }

  private def parseDate(date: String): Option[LocalDateTime] = {
    Try(LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      .toOption
  }

  /** The current system date, or provides the date part of the overridden date-time, if set
   *
   * @return today's date, or the overridden date if set
   * @see [[EdgeOfSystem.localDateTimeNow]]
   */
  def today: LocalDate = localDateTimeNow.toLocalDate

}
