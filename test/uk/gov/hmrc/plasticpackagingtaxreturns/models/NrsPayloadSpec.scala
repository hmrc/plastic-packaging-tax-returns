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

package uk.gov.hmrc.plasticpackagingtaxreturns.models

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.MockitoSugar.{mock, verify, when}
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.util.EdgeOfSystem

import java.security.MessageDigest

class NrsPayloadSpec extends PlaySpec{

  private val edgeOfSystem = mock[EdgeOfSystem]
  private val messageDigest = mock[MessageDigest]
  
  "NrsPayload" should {
    "say hi" in {
      when(edgeOfSystem.getMessageDigestSingleton) thenReturn messageDigest
      when(messageDigest.digest(any)) thenReturn Array[Byte](1, 2, 3)
      new NrsPayload(edgeOfSystem, "blah").retrievePayloadChecksum() mustBe "010203"
      verify(edgeOfSystem).getMessageDigestSingleton
      verify(messageDigest).digest(Array[Byte](0x62, 0x6c, 0x61, 0x68))
    }
  }
}
