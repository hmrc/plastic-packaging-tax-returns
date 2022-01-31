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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{ObligationDetail, ObligationStatus}

import java.time.LocalDate

class PPTObligationsSpec extends PlaySpec {

  val nextObligation = ObligationDetail(status = ObligationStatus.OPEN,
                                        inboundCorrespondenceFromDate = LocalDate.now().plusDays(1),
                                        inboundCorrespondenceToDate = LocalDate.now().plusDays(2),
                                        inboundCorrespondenceDateReceived = LocalDate.now().plusDays(3),
                                        inboundCorrespondenceDueDate = LocalDate.now().plusDays(4),
                                        periodKey = "#001"
  )

  val sut = PPTObligations(Some(nextObligation), None)

  "json writer" must {

    "write the next obligation in correct format" in {
      val json = Json.toJson(sut)
      (json \ "nextObligation" \ "periodKey").get mustBe JsString(nextObligation.periodKey)
      (json \ "nextObligation" \ "fromDate").get mustBe JsString(nextObligation.inboundCorrespondenceFromDate.toString)
      (json \ "nextObligation" \ "toDate").get mustBe JsString(nextObligation.inboundCorrespondenceToDate.toString)
      (json \ "nextObligation" \ "dueDate").get mustBe JsString(nextObligation.inboundCorrespondenceDueDate.toString)
    }
  }
}
