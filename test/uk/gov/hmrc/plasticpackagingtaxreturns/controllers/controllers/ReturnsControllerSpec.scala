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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{reset, verify, verifyNoInteractions}
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
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.{TaxReturnBuilder, TaxReturnRequestBuilder}
import uk.gov.hmrc.plasticpackagingtaxreturns.models.{
  ConvertedPackagingCredit,
  ExportedPlasticWeight,
  HumanMedicinesPlasticWeight,
  ImportedPlasticWeight,
  ManufacturedPlasticWeight,
  MetaData,
  TaxReturn
}
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository

import scala.concurrent.Future

class ReturnsControllerSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with BeforeAndAfterEach with ScalaFutures with Matchers
    with TaxReturnBuilder with TaxReturnRequestBuilder with AuthTestSupport {

  SharedMetricRegistries.clear()

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].to(mockAuthConnector), bind[TaxReturnRepository].to(taxReturnRepository))
    .build()

  private val taxReturnRepository: TaxReturnRepository = mock[TaxReturnRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(taxReturnRepository)
  }

  "POST /" should {
    val post = FakeRequest("POST", "/returns")

    "return 201" when {
      "request is valid" in {
        withAuthorizedUser()
        val request   = aTaxReturnRequest()
        val taxReturn = aTaxReturn()
        given(taxReturnRepository.create(any[TaxReturn])).willReturn(Future.successful(taxReturn))

        val result: Future[Result] = route(app, post.withJsonBody(toJson(request))).get

        status(result) must be(CREATED)
        contentAsJson(result) mustBe toJson(taxReturn)
        theCreatedTaxReturn.id mustNot be(null)
      }
    }

    "return 400" when {
      "invalid json" in {
        withAuthorizedUser()
        val payload                = Json.toJson(Map("exportedPlasticWeight" -> "{wrongKey:wrong value}")).as[JsObject]
        val result: Future[Result] = route(app, post.withJsonBody(payload)).get

        status(result) must be(BAD_REQUEST)
        contentAsJson(result) mustBe Json.obj("statusCode" -> 400, "message" -> "Bad Request")
        verifyNoInteractions(taxReturnRepository)
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(new RuntimeException())

        val result: Future[Result] = route(app, post.withJsonBody(toJson(aTaxReturnRequest()))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(taxReturnRepository)
      }
    }
  }

  "GET /:id" should {
    val get = FakeRequest("GET", "/returns/test02")

    "return 200" when {
      "request is valid" in {
        withAuthorizedUser()
        val taxReturn = aTaxReturn(withId("test02"), withConvertedPlasticPackagingCredit(1122))
        given(taxReturnRepository.findById("test02")).willReturn(Future.successful(Some(taxReturn)))

        val result: Future[Result] = route(app, get).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(taxReturn)
        verify(taxReturnRepository).findById("test02")
      }
    }

    "return 404" when {
      "id is not found" in {
        withAuthorizedUser()
        given(taxReturnRepository.findById(anyString())).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, get).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
        verify(taxReturnRepository).findById("test02")
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, get).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(taxReturnRepository)
      }
    }
  }

  "PUT /:id" should {
    val put = FakeRequest("PUT", "/returns/id01")
    "return 200" when {
      "request is valid" in {
        withAuthorizedUser()
        val request = aTaxReturnRequest(withManufacturedPlasticWeight(ManufacturedPlasticWeight(totalKg = 5)),
                                        withConvertedPlasticPackagingCredit(
                                          ConvertedPackagingCredit(totalInPence = 1433)
                                        ),
                                        withHumanMedicinesPlasticWeight(HumanMedicinesPlasticWeight(totalKg = 4)),
                                        withImportedPlasticWeight(ImportedPlasticWeight(totalKg = 2)),
                                        withDirectExportDetails(
                                          ExportedPlasticWeight(totalKg = 5, totalValueForCreditInPence = 22)
                                        ),
                                        withMetadata(MetaData(returnCompleted = true))
        )

        val taxReturn =
          aTaxReturn(withId("id01"), withManufacturedPlasticWeight(totalKg = 1, totalKgBelowThreshold = 0))

        given(taxReturnRepository.findById(anyString())).willReturn(Future.successful(Some(taxReturn)))
        given(taxReturnRepository.update(any[TaxReturn])).willReturn(Future.successful(Some(taxReturn)))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(OK)
        contentAsJson(result) mustBe toJson(taxReturn)
        val updatedTaxReturn = theUpdatedTaxReturn
        updatedTaxReturn.id mustBe "id01"
        updatedTaxReturn.manufacturedPlasticWeight.get.totalKg mustBe 5
        updatedTaxReturn.importedPlasticWeight.get.totalKg mustBe 2
        updatedTaxReturn.humanMedicinesPlasticWeight.get.totalKg mustBe 4
        updatedTaxReturn.exportedPlasticWeight.get.totalKg mustBe 5
        updatedTaxReturn.exportedPlasticWeight.get.totalValueForCreditInPence mustBe 22
        updatedTaxReturn.metaData.returnCompleted mustBe true
      }
    }

    "return 404" when {
      "tax return is not found - on find" in {
        withAuthorizedUser()
        val request = aTaxReturnRequest()
        given(taxReturnRepository.findById(anyString())).willReturn(Future.successful(None))
        given(taxReturnRepository.update(any[TaxReturn])).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
      }

      "tax return is not found - on update" in {
        withAuthorizedUser()
        val request   = aTaxReturnRequest()
        val taxReturn = aTaxReturn(withId("id"))
        given(taxReturnRepository.findById(anyString())).willReturn(Future.successful(Some(taxReturn)))
        given(taxReturnRepository.update(any[TaxReturn])).willReturn(Future.successful(None))

        val result: Future[Result] = route(app, put.withJsonBody(toJson(request))).get

        status(result) must be(NOT_FOUND)
        contentAsString(result) mustBe empty
      }
    }

    "return 401" when {
      "unauthorized" in {
        withUnauthorizedUser(InsufficientEnrolments())

        val result: Future[Result] = route(app, put.withJsonBody(toJson(aTaxReturnRequest()))).get

        status(result) must be(UNAUTHORIZED)
        verifyNoInteractions(taxReturnRepository)
      }
    }
  }

  def theCreatedTaxReturn: TaxReturn = {
    val captor: ArgumentCaptor[TaxReturn] = ArgumentCaptor.forClass(classOf[TaxReturn])
    verify(taxReturnRepository).create(captor.capture())
    captor.getValue
  }

  def theUpdatedTaxReturn: TaxReturn = {
    val captor: ArgumentCaptor[TaxReturn] = ArgumentCaptor.forClass(classOf[TaxReturn])
    verify(taxReturnRepository).update(captor.capture())
    captor.getValue
  }

}
