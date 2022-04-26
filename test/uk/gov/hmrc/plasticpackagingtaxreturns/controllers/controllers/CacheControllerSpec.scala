/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{atLeastOnce, reset, verify, verifyNoInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status, _}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.AuthTestSupport
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit.MockReturnsRepository
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.{TaxReturnBuilder, TaxReturnRequestBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.cache.UserAnswers
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.SessionRepository

import scala.concurrent.Future

class CacheControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with TaxReturnBuilder with TaxReturnRequestBuilder with AuthTestSupport with MockReturnsRepository {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector), bind[SessionRepository].to(mockSessionRepository)).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(mockSessionRepository)
  }

  "POST /" should {
    val post = FakeRequest("POST", "/cache/set/test1")

    "return 201" when {
      "request is valid" in {
        withAuthorizedUser(newUser(Some(pptEnrolment("test1"))))
        val request   = UserAnswers("id")
        given(mockSessionRepository.set(any[UserAnswers])).willReturn(Future.successful(true))

        val result: Future[Result] = route(app, post.withJsonBody(toJson(request))).get

        status(result) must be(CREATED)
        theCreatedCache.id mustNot be(null)
      }
    }

    "return 400" when {
      "invalid json" in {
        withAuthorizedUser()
        val payload                = Json.toJson(Map("anyold" -> "{wrongKey:wrong value}")).as[JsObject]
        val result: Future[Result] = route(app, post.withJsonBody(payload)).get

        status(result) must be(BAD_REQUEST)
        contentAsJson(result) mustBe Json.obj("statusCode" -> 400, "message" -> "Bad Request")
        verifyNoInteractions(mockSessionRepository)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val request   = UserAnswers("id")

        val result: Future[Result] = route(app, post.withJsonBody(toJson(request))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockSessionRepository)
      }
    }
  }

  "GET /:id" should {
    val get = FakeRequest("GET", "/cache/get/test02")

    "return 200" when {
      "request is valid" in {
        withAuthorizedUser(newUser(Some(pptEnrolment("test02"))))

        val request     = UserAnswers("id")
        val userAnswers = UserAnswers("id")

        given(mockSessionRepository.get(any())).willReturn(Future.successful(Some(request)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(userAnswers)
        verify(mockSessionRepository, atLeastOnce()).get(any())
      }
    }

    "return 404" when {
      "id is not found" in {
        val user = newUser(Some(pptEnrolment("test02")))
        withAuthorizedUser(user)
        given(mockSessionRepository.get(anyString())).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, get).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
        verify(mockSessionRepository, atLeastOnce()).get(user.internalId.get)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, get).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(mockSessionRepository)
      }
    }
  }

  def theCreatedCache: UserAnswers = {
    val captor: ArgumentCaptor[UserAnswers] = ArgumentCaptor.forClass(classOf[UserAnswers])
    verify(mockSessionRepository).set(captor.capture())
    captor.getValue
  }

}
