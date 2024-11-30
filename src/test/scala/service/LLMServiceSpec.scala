package service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import gRPCService.lambda.{ResponseMetadata, TextGenerationRequest, TextGenerationResponse}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class LLMServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterAll {
  private val testKit = ActorTestKit()
  implicit val system: ActorSystem[Nothing] = testKit.system
  implicit val ec: ExecutionContext = system.executionContext

  // Test config
  val config: Config = ConfigFactory.parseString(
    """
      |api.gateway.url = "http://test-api-gateway.com"
      |""".stripMargin)

  // Mocked dependencies
  val mockApiGatewayService: APIGatewayService = mock[APIGatewayService]

  // System under test
  val llmService = new LLMService(config, mockApiGatewayService)

  "LLMService" should {
    "successfully generate response" in {
      val testQuery = "Test query"
      val expectedResponse = "Generated response"

      when(mockApiGatewayService.generateText(testQuery)).thenReturn(Future.successful(expectedResponse))

      val result = llmService.generateResponse(testQuery)

      whenReady(result) { response =>
        response shouldBe expectedResponse
        verify(mockApiGatewayService, times(1)).generateText(testQuery)
      }
    }

    "handle failure in response generation" in {
      val testQuery = "Failed query"
      val expectedException = new RuntimeException("Test error")

      when(mockApiGatewayService.generateText(testQuery)).thenReturn(Future.failed(expectedException))

      val result = llmService.generateResponse(testQuery)

      whenReady(result.failed) { exception =>
        exception shouldBe expectedException
        verify(mockApiGatewayService, times(1)).generateText(testQuery)
      }
    }
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}