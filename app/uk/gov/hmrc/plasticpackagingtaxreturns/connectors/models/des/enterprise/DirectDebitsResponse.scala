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

package uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise

import play.api.libs.json.{Json, OFormat}

final case class DirectDebitDetail(
                                    directDebitInstructionNumber: String,
                                    directDebitPlanType: String,
                                    dateCreated: String,
                                    accountHolderName: String,
                                    sortCode: String,
                                    accountNumber: String,
                                    status: String
                                  )

object DirectDebitDetail {
  implicit val formats: OFormat[DirectDebitDetail] = Json.format[DirectDebitDetail]
}

final case class DirectDebitResponse(directDebitMandateFound: Boolean, directDebitDetails: Seq[DirectDebitDetail])

object DirectDebitResponse {
  implicit val formats: OFormat[DirectDebitResponse] = Json.format[DirectDebitResponse]
}
