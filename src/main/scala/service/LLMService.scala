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
case class Response(text: String, metadata: ResponseMetadata)
case class ResponseMetadata(
                             timestamp: Long,
                             queryLength: Int,
                             responseLength: Int,
                             processingTimeMs: Long
                           )
case class ConversationLog(
                            id: String,
                            query: String,
                            response: String,
                            timestamp: Long
                          )
case class ConversationResponse(
                                 currentResponse: String,
                                 conversationHistory: List[ConversationTurn],
                                 metadata: ResponseMetadata
                               )

// JSON formatting
object JsonProtocol extends DefaultJsonProtocol {
  implicit val queryFormat: RootJsonFormat[Query] = jsonFormat1(Query)
  implicit val responseMetadataFormat: RootJsonFormat[ResponseMetadata] = jsonFormat4(ResponseMetadata)
  implicit val conversationTurnFormat: RootJsonFormat[ConversationTurn] = jsonFormat4(ConversationTurn)
  implicit val conversationResponseFormat: RootJsonFormat[ConversationResponse] = jsonFormat3(ConversationResponse)
  implicit val conversationLogFormat: RootJsonFormat[ConversationLog] = jsonFormat4(ConversationLog)
}

// Service for handling LLM operations
trait LLMServiceTrait {
  def generateResponse(query: String)(implicit system: ActorSystem[_]): Future[String]
  def startConversation(query: String)(implicit system: ActorSystem[_]): Future[List[ConversationTurn]]
}

class LLMService(config: Config)(implicit ec: ExecutionContext) extends LLMServiceTrait {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val apiGatewayService = new APIGatewayService(config)
  private val ollamaService = new OllamaService(config)
  private val conversationManager = new ConversationManager(config, this, ollamaService)

  override def generateResponse(query: String)(implicit system: ActorSystem[_]): Future[String] = {
    logger.info(s"Generating response for query: $query")
    apiGatewayService.generateText(query)
      .recover {
        case e: Exception =>
          logger.error(s"Error generating response: ${e.getMessage}", e)
          throw e
      }
  }

  override def startConversation(query: String)(implicit system: ActorSystem[_]): Future[List[ConversationTurn]] = {
    logger.info(s"Starting conversation with query: $query")
    conversationManager.startConversation(query)
  }

  def shutdown(): Unit = {
    logger.info("Shutting down LLM Service")
    apiGatewayService.shutdown()
    ollamaService.shutdown()
  }
}

class LLMRoutes(llmService: LLMService)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  import JsonProtocol._
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def logConversation(query: String, response: String): Unit = {
    val log = ConversationLog(
      id = java.util.UUID.randomUUID().toString,
      query = query,
      response = response,
      timestamp = System.currentTimeMillis()
    )
    logger.info(s"Conversation logged: $log")
  }

  val routes: Route = {
    pathPrefix("api" / "v1") {
      concat(
        path("chat") {
          post {
            entity(as[Query]) { query =>
              val startTime = System.currentTimeMillis()

              onComplete(llmService.startConversation(query.text)) {
                case Success(conversationHistory) =>
                  val endTime = System.currentTimeMillis()

                  // Get the final response from the conversation
                  val lastTurn = conversationHistory.lastOption.map(_.bedrockResponse)
                    .getOrElse("No response generated")

                  val metadata = ResponseMetadata(
                    timestamp = endTime,
                    queryLength = query.text.length,
                    responseLength = lastTurn.length,
                    processingTimeMs = endTime - startTime
                  )

                  logConversation(query.text, lastTurn)
                  complete(ConversationResponse(lastTurn, conversationHistory, metadata))

                case Failure(ex) =>
                  logger.error(s"Error in conversation: ${ex.getMessage}", ex)
                  complete((StatusCodes.InternalServerError, s"Error in conversation: ${ex.getMessage}"))
              }
            }
          }
        },
        path("health") {
          get {
            complete(StatusCodes.OK -> "Service is healthy")
          }
        }
      )
    }
  }
}