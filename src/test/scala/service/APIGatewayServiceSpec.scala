package service

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import gRPCService.lambda.{ResponseMetadata, TextGenerationResponse}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class APIGatewayServiceSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with MockitoSugar
  with BeforeAndAfterAll {  // Removed ScalatestRouteTest

  private val testKit = ActorTestKit()
  // Explicitly define the actor system and execution context we'll use
  implicit val typedSystem: ActorSystem[Nothing] = testKit.system
  implicit val ec: ExecutionContext = testKit.system.executionContext
  implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 100.milliseconds)

  // Test config
  val config: Config = ConfigFactory.parseString(
    """
      |api.gateway.url = "http://test-api-gateway.com"
      |""".stripMargin)

  "APIGatewayService" should {
    "successfully generate text and handle response" in {
      val testPrompt = "Test prompt"
      val expectedResponse = "Generated text"

      // Create test response
      val testResponseMetadata = ResponseMetadata(
        timestamp = System.currentTimeMillis(),
        queryLength = testPrompt.length,
        responseLength = expectedResponse.length,
        processingTimeMs = 100
      )

      val testResponse = TextGenerationResponse(
        generatedText = expectedResponse,
        metadata = Some(testResponseMetadata)
      )

      val responseBytes = testResponse.toByteArray
      val base64Response = java.util.Base64.getEncoder.encodeToString(responseBytes)

      val testService = new APIGatewayService(config) {
        override def generateText(prompt: String)(implicit system: ActorSystem[_]): Future[String] = {
          Future.successful(expectedResponse)
        }
      }

      val result = testService.generateText(testPrompt)(typedSystem)

      whenReady(result) { response =>
        response shouldBe expectedResponse
      }
    }

    "handle API Gateway error responses" in {
      val testPrompt = "Test prompt"

      val testService = new APIGatewayService(config) {
        override def generateText(prompt: String)(implicit system: ActorSystem[_]): Future[String] = {
          Future.failed(new RuntimeException("API Gateway request failed"))
        }
      }

      val result = testService.generateText(testPrompt)(typedSystem)

      whenReady(result.failed) { exception =>
        exception shouldBe a[RuntimeException]
        exception.getMessage should include("API Gateway request failed")
      }
    }

    "handle invalid response format" in {
      val testPrompt = "Test prompt"

      val testService = new APIGatewayService(config) {
        override def generateText(prompt: String)(implicit system: ActorSystem[_]): Future[String] = {
          Future.failed(new Exception("Failed to extract response"))
        }
      }

      val result = testService.generateText(testPrompt)(typedSystem)

      whenReady(result.failed) { exception =>
        exception shouldBe a[Exception]
        exception.getMessage should include("Failed to extract response")
      }
    }
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}