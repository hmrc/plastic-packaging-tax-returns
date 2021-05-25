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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.repositories

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries, Timer}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import reactivemongo.api.ReadConcern
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONLong
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.builders.TaxReturnBuilder
import uk.gov.hmrc.plasticpackagingtaxreturns.models.TaxReturn
import uk.gov.hmrc.plasticpackagingtaxreturns.repositories.TaxReturnRepository

import scala.concurrent.ExecutionContext.Implicits.global

class TaxReturnRepositoryItSpec
    extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterEach with IntegrationPatience
    with TaxReturnBuilder with DefaultAwaitTimeout {

  private val injector = {
    SharedMetricRegistries.clear()
    GuiceApplicationBuilder().injector()
  }

  private val repository = injector.instanceOf[TaxReturnRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
    repository.removeAll().futureValue
  }

  private def collectionSize: Int =
    repository.collection
      .count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
      .futureValue
      .toInt

  private def givenTaxReturnExists(taxReturns: TaxReturn*): Unit =
    repository.collection.insert(ordered = true).many(taxReturns).futureValue

  "MongoRepository" should {
    "update existing index on first use" in {
      ensureExpiryTtlOnIndex(injector.instanceOf[AppConfig].dbTimeToLiveInSeconds)

      val repository = new GuiceApplicationBuilder().configure(
        Map("mongodb.timeToLiveInSeconds" -> 33)
      ).build().injector.instanceOf[TaxReturnRepository]
      await(repository.create(aTaxReturn()))

      ensureExpiryTtlOnIndex(33)
    }

    "create new ttl index when none exist" in {
      await(this.repository.collection.indexesManager.dropAll())

      val repository = new GuiceApplicationBuilder().configure(
        Map("mongodb.timeToLiveInSeconds" -> 99)
      ).build().injector.instanceOf[TaxReturnRepository]
      await(repository.create(aTaxReturn()))

      ensureExpiryTtlOnIndex(99)
    }

    def ensureExpiryTtlOnIndex(ttlSeconds: Int): Unit =
      eventually(timeout(Span(5, Seconds))) {
        {
          val indexes = await(repository.collection.indexesManager.list())
          val expiryTtl =
            indexes.find(index => index.eventualName == "ttlIndexReturns").map(getExpireAfterSecondsOptionOf)
          expiryTtl.get mustBe ttlSeconds
        }
      }

    def getExpireAfterSecondsOptionOf(idx: Index): Long =
      idx.options.getAs[BSONLong]("expireAfterSeconds").getOrElse(BSONLong(0)).as[Long]
  }

  "Create" should {
    "persist the tax return" in {
      val taxReturn = aTaxReturn()
      repository.create(taxReturn).futureValue mustBe taxReturn

      collectionSize mustBe 1
    }
    "update lastModifiedDateTime field" in {
      val taxReturn = aTaxReturn()

      await(repository.create(taxReturn))
      val newRegistration = await(repository.findById(taxReturn.id))

      newRegistration.get.lastModifiedDateTime must not be None
    }
  }

  "Update" should {
    "update the tax return" in {
      givenTaxReturnExists(aTaxReturn())
      val taxReturn = aTaxReturn(withManufacturedPlasticWeight(totalKg = 5),
                                 withImportedPlasticWeight(totalKg = 3),
                                 withHumanMedicinesPlasticWeight(totalKg = 1),
                                 withConvertedPlasticPackagingCredit(totalPence = 1010),
                                 withRecycledPlasticWeight(totalKg = 13)
      )

      val updatedTaxReturn = await(repository.update(taxReturn)).get

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

      repository.update(taxReturn).futureValue mustBe None

      collectionSize mustBe 0

      getTimer("ppt.returns.mongo.update").getCount mustBe 1
    }

    "update lastModifiedDateTime on each registration update" in {
      val taxReturn = aTaxReturn()
      await(repository.create(taxReturn))
      val initialLastModifiedDateTime =
        await(repository.findById(taxReturn.id)).get.lastModifiedDateTime.get

      val updatedRegistration = repository.update(taxReturn).futureValue.get

      updatedRegistration.lastModifiedDateTime.get.isAfter(initialLastModifiedDateTime) mustBe true
    }

  }

  "Find by ID" should {
    "return the persisted tax return" when {
      "one exists with ID" in {
        val taxReturn = aTaxReturn(withManufacturedPlasticWeight(totalKg = 5),
                                   withImportedPlasticWeight(totalKg = 3),
                                   withHumanMedicinesPlasticWeight(totalKg = 1),
                                   withConvertedPlasticPackagingCredit(totalPence = 1010),
                                   withDirectExportDetails(totalKg = 8),
                                   withRecycledPlasticWeight(totalKg = 13),
                                   withMetadata(returnCompleted = true)
        )

        givenTaxReturnExists(taxReturn)

        repository.findById(taxReturn.id).futureValue mustBe Some(taxReturn)

        // this indicates that a timer has started and has been stopped
        getTimer("ppt.returns.mongo.find").getCount mustBe 1
      }
    }

    "return None" when {
      "none exist with id" in {
        val taxReturn1 = aTaxReturn(withId("some-other-id"))
        val taxReturn2 = aTaxReturn()
        givenTaxReturnExists(taxReturn1, taxReturn2)

        repository.findById("non-existing-id").futureValue mustBe None

        getTimer("ppt.returns.mongo.find").getCount mustBe 1
      }
    }
  }

  "Delete" should {
    "remove the tax return" in {
      val taxReturn = aTaxReturn()
      givenTaxReturnExists(taxReturn)

      repository.delete(taxReturn).futureValue

      collectionSize mustBe 0
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
