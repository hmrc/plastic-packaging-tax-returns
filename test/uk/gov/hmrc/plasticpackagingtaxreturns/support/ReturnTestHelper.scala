/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.plasticpackagingtaxreturns.support

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse

import java.time.LocalDate

object ReturnTestHelper {

  val returnsWithNoCreditDataJson: JsObject = Json.parse(s"""{
       |        "obligation" : {
       |            "periodKey" : "22C2",
       |            "fromDate" : "${LocalDate.now.minusMonths(1)}",
       |            "toDate" : "${LocalDate.now}"
       |        },
       |        "manufacturedPlasticPackagingWeight" : 100,
       |        "importedPlasticPackagingWeight" : 0,
       |        "exportedPlasticPackagingWeight" : 0,
       |        "anotherBusinessExportWeight": 0,

       |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
       |        "nonExportRecycledPlasticPackagingWeight" : 5
       |    }""".stripMargin).asInstanceOf[JsObject]

  val returnWithCreditsDataJson: JsObject =
    Json.parse(s"""{
       |        "obligation" : {
       |            "periodKey" : "21C4", 
       |            "fromDate" : "2022-10-01",
       |            "toDate" : "2022-12-31",
       |            "dueDate" : "2023-01-28"
       |        },
       |        "whatDoYouWantToDo" : true,
       |        "manufacturedPlasticPackagingWeight" : 100,
       |        "importedPlasticPackagingWeight" : 1,
       |        "exportedPlasticPackagingWeight" : 200,
       |        "anotherBusinessExportWeight" : 100,
       |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
       |        "nonExportRecycledPlasticPackagingWeight" : 5,
       |        "convertedCredits": {
       |          "weight": "200"
       |        }
       |    }""".stripMargin).asInstanceOf[JsObject]

  val invalidReturnsDataJson: JsObject =
    Json.parse(s"""{
         |        "obligation" : {
         |            "periodKey" : "21C4",
         |            "fromDate" : "${LocalDate.now.minusMonths(1)}",
         |            "toDate" : "${LocalDate.now}",
         |            "dueDate" : "2023-01-28"
         |        },
         |        "importedPlasticPackagingWeight" : 0,
         |        "exportedPlasticPackagingWeight" : 0,
         |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 10,
         |        "nonExportRecycledPlasticPackagingWeight" : 5
         |    }""".stripMargin).asInstanceOf[JsObject]

  def returnWithLegacyCreditData: String =
    """{
  |        "obligation" : {
  |            "fromDate" : "2023-01-01",
  |            "toDate" : "2023-03-31",
  |            "dueDate" : "2023-05-31",
  |            "periodKey" : "23C1"
  |        },
  |        "isFirstReturn" : false,
  |        "startYourReturn" : true,
  |        "whatDoYouWantToDo" : true,
  |        "exportedCredits" : {
  |            "yesNo" : true,
  |            "weight" : 12
  |        },
  |        "convertedCredits" : {
  |            "yesNo" : true,
  |            "weight" : 34
  |        },
  |        "manufacturedPlasticPackaging" : false,
  |        "manufacturedPlasticPackagingWeight" : 0,
  |        "importedPlasticPackaging" : false,
  |        "importedPlasticPackagingWeight" : 0,
  |        "directlyExportedComponents" : false,
  |        "exportedPlasticPackagingWeight" : 0,
  |        "plasticExportedByAnotherBusiness" : false,
  |        "anotherBusinessExportWeight" : 0,
  |        "nonExportedHumanMedicinesPlasticPackaging" : false,
  |        "nonExportedHumanMedicinesPlasticPackagingWeight" : 0,
  |        "nonExportRecycledPlasticPackaging" : false,
  |        "nonExportRecycledPlasticPackagingWeight" : 0
  |    }""".stripMargin

  def createCreditBalanceDisplayResponse =
    ExportCreditBalanceDisplayResponse(
      processingDate = "2021-11-17T09:32:50.345Z",
      totalPPTCharges = BigDecimal(1000),
      totalExportCreditClaimed = BigDecimal(100),
      totalExportCreditAvailable = BigDecimal(200)
    )

}
