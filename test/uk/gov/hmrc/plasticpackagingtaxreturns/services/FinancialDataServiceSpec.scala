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

package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.FinancialDataConnector

import java.time.LocalDate
import scala.concurrent.Future

class FinancialDataServiceSpec
    extends PlaySpec with MockitoSugar with BeforeAndAfterEach with FutureAwaits with DefaultAwaitTimeout {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
    when(mockConnector.get(any(), any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(
      Future.successful(Left(0))
    )
  }

  val mockConnector: FinancialDataConnector = mock[FinancialDataConnector]
  val sut: FinancialDataService             = new FinancialDataService(mockConnector)

  val hc: HeaderCarrier = HeaderCarrier()

  "getRaw" must {
    "just pass through params" in {
      await(sut.getRaw("pptRef", LocalDate.now(), LocalDate.now(), Some(true), Some(false), Some(true), None, "someId")(hc))

      verify(mockConnector).get("pptRef",
        Some(LocalDate.now()),
        Some(LocalDate.now()),
        Some(true),
        Some(false),
        Some(true),
        None,
        "someId"
      )(hc)
    }
  }

  "getFinancials" must {
    "default params" in {
      await(sut.getFinancials("pptRef", "someId")(hc))

      verify(mockConnector).get("pptRef", None, None, Some(true), Some(true), Some(true), None, "someId")(hc)
    }
  }

}
