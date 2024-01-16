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

package uk.gov.hmrc.plasticpackagingtaxreturns.support

import play.api.libs.json.{JsObject, Json}

object AmendTestHelper {

  val userAnswersDataAmends: JsObject = Json.parse(s"""{
       |        "amendSelectedPeriodKey": "21C4",
       |        "returnDisplayApi" : {
       |            "idDetails" : {
       |                "pptReferenceNumber" : "pptref",
       |                "submissionId" : "submission12"
       |            },
       |            "returnDetails" : {
       |                "manufacturedWeight" : 250,
       |                "importedWeight" : 0,
       |                "totalNotLiable" : 0,
       |                "humanMedicines" : 10,
       |                "directExports" : 0,
       |                "recycledPlastic" : 5,
       |                "creditForPeriod" : 12,
       |                "debitForPeriod" : 0,
       |                "totalWeight" : 220,
       |                "taxDue" : 44
       |            }
       |        },
       |        "amend": {
       |           "obligation" : {
       |              "periodKey" : "21C4",
       |              "toDate" : "2022-06-30",
       |              "dueDate" : "2023-01-28"
       |           },
       |           "amendManufacturedPlasticPackaging" : 100,
       |           "amendImportedPlasticPackaging" : 1,
       |           "amendDirectExportPlasticPackaging" : 2,
       |           "amendExportedByAnotherBusinessPlasticPackaging": 5,
       |           "amendHumanMedicinePlasticPackaging" : 3,
       |           "amendRecycledPlasticPackaging" : 5
       |        }
       |    }""".stripMargin).asInstanceOf[JsObject]

  val userAnswersDataWithInvalidAmends: JsObject = Json.parse(s"""{
       |        "amendSelectedPeriodKey": "22C2",
       |        "returnDisplayApi" : {
       |            "idDetails" : {
       |                "pptReferenceNumber" : "pptref",
       |                "submissionId" : "submission12"
       |            },
       |            "returnDetails" : {
       |                "manufacturedWeight" : 250,
       |                "importedWeight" : 0,
       |                "totalNotLiable" : 0,
       |                "humanMedicines" : 10,
       |                "recycledPlastic" : 5,
       |                "creditForPeriod" : 12,
       |                "debitForPeriod" : 0,
       |                "totalWeight" : 20,
       |                "taxDue" : 44
       |            }
       |        },
       |        "amend": {
       |           "obligation" : {
       |              "periodKey" : "22C2",
       |              "toDate" : "2022-06-30",
       |              "dueDate" : "2023-01-28"
       |           },
       |           "amendManufacturedPlasticPackaging" : 100,
       |           "amendImportedPlasticPackaging" : 0,
       |           "amendHumanMedicinePlasticPackaging" : 10,
       |           "amendRecycledPlasticPackaging" : 5
       |        }
       |    }""".stripMargin).asInstanceOf[JsObject]

  val userAnswersDataWithoutAmends: JsObject = Json.parse(s"""{
       |        "amendSelectedPeriodKey": "21C4",
       |        "returnDisplayApi" : {
       |            "idDetails" : {
       |                "pptReferenceNumber" : "pptref",
       |                "submissionId" : "submission12"
       |            },
       |            "returnDetails" : {
       |                "manufacturedWeight" : 255,
       |                "importedWeight" : 0,
       |                "totalNotLiable" : 0,
       |                "directExports" : 6,
       |                "humanMedicines" : 10,
       |                "recycledPlastic" : 5,
       |                "creditForPeriod" : 12,
       |                "debitForPeriod" : 0,
       |                "totalWeight" : 20,
       |                "taxDue" : 44
       |            }
       |        },
       |        "amend": {
       |           "obligation" : {
       |              "periodKey" : "22C2",
       |              "toDate" : "2022-06-30",
       |              "dueDate" : "2023-01-28"
       |           }
       |        }
       | }""".stripMargin).asInstanceOf[JsObject]

  val userAnswersDataWithoutKey: JsObject = Json.parse(s"""{
       |        "returnDisplayApi" : {
       |            "idDetails" : {
       |                "pptReferenceNumber" : "pptref",
       |                "submissionId" : "submission12"
       |            },
       |            "returnDetails" : {
       |                "manufacturedWeight" : 255,
       |                "importedWeight" : 0,
       |                "totalNotLiable" : 0,
       |                "directExports" : 6,
       |                "humanMedicines" : 10,
       |                "recycledPlastic" : 5,
       |                "creditForPeriod" : 12,
       |                "debitForPeriod" : 0,
       |                "totalWeight" : 20,
       |                "taxDue" : 44
       |            }
       |        }
       | }""".stripMargin).asInstanceOf[JsObject]

}
