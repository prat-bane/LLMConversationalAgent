package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// Domain models
case class Query(text: String)
case class Response(text: String)

// JSON formatting
object JsonProtocol extends DefaultJsonProtocol {
  implicit val queryFormat: RootJsonFormat[Query] = jsonFormat1(Query)
  implicit val responseFormat: RootJsonFormat[Response] = jsonFormat1(Response)

}

// Service for handling LLM operations
trait LLMServiceTrait {
  def generateResponse(query: String)(implicit system: ActorSystem[_]): Future[String]
}

class LLMService(config: Config, apiGatewayService:APIGatewayService)(implicit ec: ExecutionContext) extends LLMServiceTrait {
  private val logger = LoggerFactory.getLogger(this.getClass)


  override def generateResponse(query: String)(implicit system: ActorSystem[_]): Future[String] = {
    logger.info(s"Generating response for query: $query")
    apiGatewayService.generateText(query)
      .recover {
        case e: Exception =>
          logger.error(s"Error generating response: ${e.getMessage}", e)
          throw e
      }
  }


  def shutdown(): Unit = {
    logger.info("Shutting down LLM Service")
    apiGatewayService.shutdown()
  }
}

class LLMRoutes(llmService: LLMService)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  import JsonProtocol._
  private val logger = LoggerFactory.getLogger(this.getClass)


  val routes: Route = {
    pathPrefix("api" / "v1") {
      path("chat") {
        post {
          entity(as[Query]) { query =>
            onComplete(llmService.generateResponse(query.text)) {
              case Success(generatedText) =>
                complete(Response(generatedText))
              case Failure(ex) =>
                logger.error(s"Error generating response: ${ex.getMessage}", ex)
                complete(StatusCodes.InternalServerError -> s"Error: ${ex.getMessage}")
            }
          }
        }
      }
    }
  }
}