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

package uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation

import play.api.libs.json._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

case class NonRepudiationMetadata(
  businessId: String,
  notableEvent: String,
  payloadContentType: String,
  payloadSha256Checksum: String,
  userSubmissionTimestamp: String,
  identityData: IdentityData,
  userAuthToken: String,
  headerData: Map[String, String],
  searchKeys: Map[String, String]
)

object NonRepudiationMetadata {

  def create(
    notableEvent: String,
    pptReference: String,
    userHeaders: Map[String, String],
    identityData: IdentityData,
    userAuthToken: String,
    payloadChecksum: String,
    submissionTimestamp: ZonedDateTime
  ): NonRepudiationMetadata = {

    val submissionTimestampAsString: String = submissionTimestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    NonRepudiationMetadata(
      businessId = "ppt",
      notableEvent,
      payloadContentType = "application/json",
      payloadSha256Checksum = payloadChecksum,
      userSubmissionTimestamp = submissionTimestampAsString,
      identityData = identityData,
      userAuthToken = userAuthToken,
      headerData = userHeaders,
      searchKeys = Map("pptReference" -> pptReference)
    )
  }

  implicit val format: OFormat[NonRepudiationMetadata] = Json.format[NonRepudiationMetadata]

}
