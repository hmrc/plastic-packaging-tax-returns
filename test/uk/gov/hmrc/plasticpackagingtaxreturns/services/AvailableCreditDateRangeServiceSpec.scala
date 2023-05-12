package uk.gov.hmrc.plasticpackagingtaxreturns.services

import org.mockito.MockitoSugar
import org.mockito.integrations.scalatest.ResetMocksAfterEachTest
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.plasticpackagingtaxreturns.models.returns.CreditRangeOption

import java.time.LocalDate

class AvailableCreditDateRangeServiceSpec extends PlaySpec 
  with BeforeAndAfterEach with MockitoSugar with ResetMocksAfterEachTest {

  "it" should {
    "return some years" in {
      new AvailableCreditDateRangesService().calculate mustBe Seq(
        CreditRangeOption(LocalDate.of(2022, 4, 1), LocalDate.of(2023, 3, 31)),
        CreditRangeOption(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31)),
      )
    }
  }
  
}
