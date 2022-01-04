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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.repositories

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries, Timer}
import com.kenshoo.play.metrics.Metrics
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.TaxReturnBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepositoryImpl

import scala.concurrent.ExecutionContext.Implicits.global

class TaxReturnRepositorySpec
    extends AnyWordSpec with DefaultPlayMongoRepositorySupport[TaxReturn] with Matchers with ScalaFutures
    with DefaultAwaitTimeout with BeforeAndAfterEach with MockitoSugar with TaxReturnBuilder {

  private val injector = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder().injector()
  }

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.dbTimeToLiveInSeconds) thenReturn 1

  private val metrics = injector.instanceOf[Metrics]

  override lazy val repository = new TaxReturnRepositoryImpl(mongoComponent, mockAppConfig, metrics)

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  private def collectionSize: Int = count().futureValue.toInt

  private def givenTaxReturnExists(taxReturns: TaxReturn*): Unit =
    taxReturns.foreach(tax => await(insert(tax)))

  "findById" should {

    "return TaxRecord" when {

      "record with id exists" in {

        val taxReturn = aTaxReturn(withId("some-id"))
        givenTaxReturnExists(taxReturn)

        collectionSize mustBe 1

        val result = await(repository.findById("some-id"))

        result mustBe Some(taxReturn)

        // this indicates that a timer has started and has been stopped
        getTimer("ppt.returns.mongo.find").getCount mustBe 1

      }
    }

    "return None" when {

      "no record exists" in {

        collectionSize mustBe 0

        val result = await(repository.findById("does-not-exist"))

        result mustBe None

        getTimer("ppt.returns.mongo.find").getCount mustBe 1
      }
    }
  }

  "create" should {

    "persist the tax record with lastModifiedDateTime" in {

      collectionSize mustBe 0
      val taxReturn = aTaxReturn()

      taxReturn.lastModifiedDateTime mustBe None

      await(repository.create(taxReturn))

      collectionSize mustBe 1

      val saved = await(repository.findById(taxReturn.id)).get

      saved.lastModifiedDateTime must not be None

      getTimer("ppt.returns.mongo.create").getCount mustBe 1
    }

  }

  "update" should {

    "update an existing tax return" in {

      insert(aTaxReturn(withId("123"))).futureValue

      val saved = await(repository.findById("123")).get
      saved.lastModifiedDateTime mustBe None

      val taxReturn = aTaxReturn(withId("123"),
                                 withManufacturedPlasticWeight(totalKg = 5),
                                 withImportedPlasticWeight(totalKg = 3),
                                 withHumanMedicinesPlasticWeight(totalKg = 1),
                                 withConvertedPlasticPackagingCredit(totalPence = 1010),
                                 withRecycledPlasticWeight(totalKg = 13)
      )

      await(repository.update(taxReturn))

      val updatedTaxReturn = await(repository.findById("123")).get
      updatedTaxReturn.lastModifiedDateTime must not be None

      updatedTaxReturn.id mustBe taxReturn.id
      updatedTaxReturn.manufacturedPlasticWeight mustBe taxReturn.manufacturedPlasticWeight
      updatedTaxReturn.importedPlasticWeight mustBe taxReturn.importedPlasticWeight
      updatedTaxReturn.humanMedicinesPlasticWeight mustBe taxReturn.humanMedicinesPlasticWeight
      updatedTaxReturn.exportedPlasticWeight mustBe taxReturn.exportedPlasticWeight
      updatedTaxReturn.convertedPackagingCredit mustBe taxReturn.convertedPackagingCredit
      updatedTaxReturn.recycledPlasticWeight mustBe taxReturn.recycledPlasticWeight
      updatedTaxReturn.metaData mustBe taxReturn.metaData

      // this indicates that a timer has started and has been stopped
      getTimer("ppt.returns.mongo.update").getCount mustBe 1

      collectionSize mustBe 1
    }

    "do nothing for missing tax return" in {
      val taxReturn = aTaxReturn()

      await(repository.update(taxReturn)) mustBe None

      collectionSize mustBe 0

      getTimer("ppt.returns.mongo.update").getCount mustBe 1
    }

    "update lastModifiedDateTime on each registration update" in {
      val taxReturn = aTaxReturn()
      await(repository.create(taxReturn))
      val initialLastModifiedDateTime =
        await(repository.findById(taxReturn.id)).get.lastModifiedDateTime.get

      val updatedRegistration = await(repository.update(taxReturn)).get

      updatedRegistration.lastModifiedDateTime.get.isAfter(initialLastModifiedDateTime) mustBe true
    }
  }

  "delete" should {

    "remove the tax return" in {
      val taxReturn = aTaxReturn()
      givenTaxReturnExists(taxReturn)

      repository.delete(taxReturn).futureValue

      collectionSize mustBe 0

      getTimer("ppt.returns.mongo.delete").getCount mustBe 1
    }

    "maintain other tax return" when {
      "they have a different ID" in {
        val taxReturn1 = aTaxReturn()
        val taxReturn2 = aTaxReturn(withId("id1"))
        val taxReturn3 = aTaxReturn(withId("id2"))
        givenTaxReturnExists(taxReturn2, taxReturn3)

        repository.delete(taxReturn1).futureValue

        collectionSize mustBe 2
      }
    }
  }

  private def getTimer(name: String): Timer =
    SharedMetricRegistries
      .getOrCreate("plastic-packaging-tax-returns")
      .getTimers(MetricFilter.startsWith(name))
      .get(name)

}
