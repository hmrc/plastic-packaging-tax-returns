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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, spy, verify, when}
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.AvailableCreditDateRangesController
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.AuthorizedRequest
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption
import uk.gov.hmrc.plasticpackagingtaxreturns.services.AvailableCreditDateRangesService

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Try

class AvailableCreditDateRangesControllerSpec extends PlaySpec with BeforeAndAfterEach{

  private val controllerComponents = Helpers.stubControllerComponents()
  private val service = mock[AvailableCreditDateRangesService]
  private val authenticator = spy(new FakeAuthenticator(controllerComponents))

  val sut = new AvailableCreditDateRangesController(service, authenticator, controllerComponents)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(service, authenticator)
  }

  "get" must {

    "use authenticator" in {
      Try(await(sut.get("pptRef")(FakeRequest())))
      verify(authenticator).authorisedAction(any, ArgumentMatchers.eq("pptRef"))(any())

    }
    "return 200 and list of dates" in {
      when(service.get).thenReturn(
        Seq(
        CreditRangeOption(
          LocalDate.of(1996, 3, 27),
          LocalDate.of(1998, 5, 14)
        )
        )
      )
      val dates = """[{"from": "1996-03-27", "to": "1998-05-14"}]"""

      val result = sut.get("pptRef")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(dates)
      verify(service).get
    }
  }

}
