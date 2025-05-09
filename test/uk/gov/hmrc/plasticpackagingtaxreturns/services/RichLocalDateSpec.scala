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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem
import org.mockito.scalatest.ResetMocksAfterEachTest

import java.time.LocalDate

class RichLocalDateSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach with ResetMocksAfterEachTest {

  implicit val edgeOfSystem: EdgeOfSystem = mock[EdgeOfSystem]

  private val pretendThisIsTheDate = LocalDate.of(1900, 1, 2)
  private val theDayBefore         = LocalDate.of(1900, 1, 1)
  private val theSameDay           = LocalDate.of(1900, 1, 2)
  private val theDayAfter          = LocalDate.of(1900, 1, 3)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(edgeOfSystem.today) thenReturn pretendThisIsTheDate
  }

  "isEqualOrAfterToday and isBeforeToday" when {

    "it's the day before" in {
      theDayBefore.isEqualOrAfterToday mustBe false
      theDayBefore.isBeforeToday mustBe true
    }

    "it's the same day" in {
      theSameDay.isEqualOrAfterToday mustBe true
      theSameDay.isBeforeToday mustBe false
    }

    "it's the day after" in {
      theDayAfter.isEqualOrAfterToday mustBe true
      theDayAfter.isBeforeToday mustBe false
    }
  }
}
