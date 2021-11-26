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

package uk.gov.hmrc.plasticpackagingtaxreturns.controllers.base.unit

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.{
  FinancialDataResponse,
  ObligationDataResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.des.enterprise.ObligationStatus.ObligationStatus
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.exportcreditbalance.ExportCreditBalanceDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.returns.Return
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionDisplay.SubscriptionDisplayResponse
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors.models.eis.subscriptionUpdate.{
  SubscriptionUpdateRequest,
  SubscriptionUpdateResponse,
  SubscriptionUpdateSuccessfulResponse
}
import uk.gov.hmrc.plasticpackagingtaxreturns.connectors._
import uk.gov.hmrc.plasticpackagingtaxreturns.models.nonRepudiation.{
  NonRepudiationMetadata,
  NonRepudiationSubmissionAccepted
}
import java.time.LocalDate

import scala.concurrent.Future

trait MockConnectors extends MockitoSugar with BeforeAndAfterEach {
  self: Suite =>

  protected val mockSubscriptionsConnector: SubscriptionsConnector             = mock[SubscriptionsConnector]
  protected val mockExportCreditBalanceConnector: ExportCreditBalanceConnector = mock[ExportCreditBalanceConnector]
  protected val mockNonRepudiationConnector: NonRepudiationConnector           = mock[NonRepudiationConnector]
  protected val mockReturnsConnector: ReturnsConnector                         = mock[ReturnsConnector]
  protected val mockObligationDataConnector: ObligationDataConnector           = mock[ObligationDataConnector]
  protected val mockFinancialDataConnector: FinancialDataConnector             = mock[FinancialDataConnector]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSubscriptionsConnector, mockNonRepudiationConnector, mockReturnsConnector)
  }

  protected def mockGetSubscriptionFailure(
    pptReference: String,
    statusCode: Int
  ): OngoingStubbing[Future[Either[Int, SubscriptionDisplayResponse]]] =
    when(mockSubscriptionsConnector.getSubscription(ArgumentMatchers.eq(pptReference))(any[HeaderCarrier])).thenReturn(
      Future.successful(Left(statusCode))
    )

  protected def mockGetSubscription(
    pptReference: String,
    displayResponse: SubscriptionDisplayResponse
  ): OngoingStubbing[Future[Either[Int, SubscriptionDisplayResponse]]] =
    when(mockSubscriptionsConnector.getSubscription(ArgumentMatchers.eq(pptReference))(any[HeaderCarrier])).thenReturn(
      Future.successful(Right(displayResponse))
    )

  protected def mockSubscriptionSubmitFailure(ex: Exception): OngoingStubbing[Future[SubscriptionUpdateResponse]] =
    when(mockSubscriptionsConnector.updateSubscription(any(), any())(any()))
      .thenThrow(ex)

  protected def mockSubscriptionUpdate(
    pptReference: String,
    request: SubscriptionUpdateRequest,
    subscription: SubscriptionUpdateSuccessfulResponse
  ): OngoingStubbing[Future[SubscriptionUpdateResponse]] =
    when(
      mockSubscriptionsConnector.updateSubscription(ArgumentMatchers.eq(pptReference), ArgumentMatchers.eq(request))(
        any[HeaderCarrier]
      )
    ).thenReturn(Future.successful(subscription))

  protected def mockNonRepudiationSubmission(
    testEncodedPayload: String,
    expectedMetadata: NonRepudiationMetadata,
    response: NonRepudiationSubmissionAccepted
  )(implicit hc: HeaderCarrier): OngoingStubbing[Future[NonRepudiationSubmissionAccepted]] =
    when(
      mockNonRepudiationConnector.submitNonRepudiation(ArgumentMatchers.eq(testEncodedPayload),
                                                       ArgumentMatchers.eq(expectedMetadata)
      )(ArgumentMatchers.eq(hc))
    ).thenReturn(Future.successful(response))

  protected def mockNonRepudiationSubmissionFailure(
    ex: Exception
  ): OngoingStubbing[Future[NonRepudiationSubmissionAccepted]] =
    when(mockNonRepudiationConnector.submitNonRepudiation(any(), any())(any()))
      .thenThrow(ex)

  protected def mockGetExportCreditBalance(
    pptReference: String,
    displayResponse: ExportCreditBalanceDisplayResponse
  ): OngoingStubbing[Future[Either[Int, ExportCreditBalanceDisplayResponse]]] =
    when(
      mockExportCreditBalanceConnector.getBalance(ArgumentMatchers.eq(pptReference),
                                                  any[LocalDate](),
                                                  any[LocalDate]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Right(displayResponse)))

  protected def mockGetExportCreditBalanceFailure(
    pptReference: String,
    statusCode: Int
  ): OngoingStubbing[Future[Either[Int, ExportCreditBalanceDisplayResponse]]] =
    when(
      mockExportCreditBalanceConnector.getBalance(ArgumentMatchers.eq(pptReference),
                                                  any[LocalDate](),
                                                  any[LocalDate]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Left(statusCode)))

  protected def mockReturnsSubmissionConnector(resp: Return) =
    when(mockReturnsConnector.submitReturn(any(), any())(any())).thenReturn(Future.successful(Right(resp)))

  protected def mockReturnsSubmissionConnectorFailure(statusCode: Int) =
    when(mockReturnsConnector.submitReturn(any(), any())(any())).thenReturn(Future.successful(Left(statusCode)))

  protected def mockReturnDisplayConnector(resp: Return) =
    when(mockReturnsConnector.get(any(), any())(any())).thenReturn(Future.successful(Right(resp)))

  protected def mockReturnDisplayConnectorFailure(statusCode: Int) =
    when(mockReturnsConnector.get(any(), any())(any())).thenReturn(Future.successful(Left(statusCode)))

  protected def mockGetObligationData(
    pptReference: String,
    displayResponse: ObligationDataResponse
  ): OngoingStubbing[Future[Either[Int, ObligationDataResponse]]] =
    when(
      mockObligationDataConnector.get(ArgumentMatchers.eq(pptReference),
                                      any[LocalDate](),
                                      any[LocalDate](),
                                      any[ObligationStatus]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Right(displayResponse)))

  protected def mockGetObligationDataFailure(
    pptReference: String,
    statusCode: Int
  ): OngoingStubbing[Future[Either[Int, ObligationDataResponse]]] =
    when(
      mockObligationDataConnector.get(ArgumentMatchers.eq(pptReference),
                                      any[LocalDate](),
                                      any[LocalDate](),
                                      any[ObligationStatus]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Left(statusCode)))

  protected def mockGetFinancialData(
    pptReference: String,
    response: FinancialDataResponse
  ): OngoingStubbing[Future[Either[Int, FinancialDataResponse]]] =
    when(
      mockFinancialDataConnector.get(ArgumentMatchers.eq(pptReference),
                                     any[LocalDate](),
                                     any[LocalDate](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Right(response)))

  protected def mockGetFinancialDataFailure(
    pptReference: String,
    statusCode: Int
  ): OngoingStubbing[Future[Either[Int, FinancialDataResponse]]] =
    when(
      mockFinancialDataConnector.get(ArgumentMatchers.eq(pptReference),
                                     any[LocalDate](),
                                     any[LocalDate](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]](),
                                     any[Option[Boolean]]()
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(Left(statusCode)))

}
