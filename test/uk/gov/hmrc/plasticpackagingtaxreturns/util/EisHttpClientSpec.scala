package uk.gov.hmrc.plasticpackagingtaxreturns.util

import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.{mock, reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient => HmrcClient, HttpResponse => HmrcResponse}
import uk.gov.hmrc.plasticpackagingtaxreturns.config.AppConfig
import uk.gov.hmrc.plasticpackagingtaxreturns.util.PurplePrint.purplePrint

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EisHttpClientSpec extends PlaySpec with BeforeAndAfterEach {

  private val hmrcClient = mock[HmrcClient]
  private val appConfig = mock[AppConfig]

  private val eisHttpClient = new EisHttpClient(hmrcClient, appConfig)
  private implicit val headerCarrier: HeaderCarrier = mock[HeaderCarrier]
  
  case class ExampleModel(vitalData: Int = 1)

  private val exampleModel = ExampleModel()
  private implicit val writes: OWrites[ExampleModel] = Json.writes[ExampleModel]
  
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(hmrcClient, appConfig, headerCarrier)
    when(hmrcClient.PUT[Any, Any](any, any, any) (any, any, any, any)) thenReturn Future.successful(HmrcResponse(200, "{}"))
    when(appConfig.eisEnvironment) thenReturn "space"
  }

  "it" should {
    
    "quick check" in {
      when(appConfig.eisEnvironment) thenReturn "brie"
      appConfig.eisEnvironment mustBe "brie"
      Seq("Environment" -> appConfig.eisEnvironment) mustBe List(("Environment", "brie"))
    }

    "send a put request" in {
      val response = await { eisHttpClient.put("proto://some:port/endpoint", exampleModel) }
      response mustBe HttpResponse(200, "{}")
      verify(hmrcClient).PUT[ExampleModel, HttpResponse](eqTo("proto://some:port/endpoint"), eqTo(exampleModel), any) (any, 
        any, any, any)

      withClue("with these headers") {
        val headers = Seq("Environment" -> "space")
        verify(appConfig).eisEnvironment
        verify(hmrcClient).PUT[Any, Any](any, any, eqTo(headers)) (any, any, any, any)
      }
    }

  }
}
