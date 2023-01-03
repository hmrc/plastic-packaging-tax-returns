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

package uk.gov.hmrc.plasticpackagingtaxreturns

import java.time.{LocalDate, ZoneOffset}

package object services {

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toEpochDay)

  implicit class RichLocalDate(val localDate: LocalDate) extends AnyVal {

    private def today: LocalDate = LocalDate.now(ZoneOffset.UTC)

    def isEqualOrAfterToday: Boolean =
      localDate.compareTo(today) >= 0

    def isBeforeToday: Boolean =
      localDate.isBefore(today)

  }

}
