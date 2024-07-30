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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it

import play.api.libs.json.Reads
import play.api.mvc.{Action, BodyParser, ControllerComponents, Result}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.actions.{Authenticator, AuthorizedRequest}
import uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.it.FakeAuthenticator.{internalID, pptRef}

import javax.inject.Inject
import scala.concurrent.Future

class FakeAuthenticator @Inject() (cc: ControllerComponents) extends Authenticator {

  override def authorisedAction[A](
    bodyParser: BodyParser[A],
    ppt: String
  )(body: AuthorizedRequest[A] => Future[Result]): Action[A] =
    cc.actionBuilder.async(bodyParser) { implicit request =>
      body(AuthorizedRequest(pptRef, request, internalID))
    }

  override def parsingJson[T](implicit rds: Reads[T]): BodyParser[T] = ???
}

object FakeAuthenticator {
  val pptRef     = "some-ppt-ref"
  val internalID = "some-internal-ID"
  def cacheKey   = s"$internalID-$pptRef"
}
