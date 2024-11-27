package service

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

case class ConversationTurn(
                             query: String,
                             bedrockResponse: String,
                             ollamaResponse: String,
                             timestamp: Long
                           )

class ConversationManager(
                           config: Config,
                           llmService: LLMService,
                           ollamaService: OllamaService
                         )(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(this.getClass)
  private var conversationHistory = List[ConversationTurn]()

  def startConversation(initialQuery: String)(implicit system: ActorSystem[_]): Future[List[ConversationTurn]] = {
    logger.info(s"Starting conversation with initial query: $initialQuery")

    def conversationLoop(
                          currentQuery: String,
                          turnCount: Int = 0
                        ): Future[List[ConversationTurn]] = {
      if (!ollamaService.shouldContinueConversation(turnCount)) {
        logger.info(s"Conversation ended after $turnCount turns")
        Future.successful(conversationHistory.reverse)
      } else {
        for {
          // Get response from Bedrock through LLM service
          bedrockResponse <- llmService.generateResponse(currentQuery)
          _ = logger.info(s"Received Bedrock response: $bedrockResponse")

          // Generate next query using Ollama
          ollamaResponse <- ollamaService.generateNextQuery(bedrockResponse)
          _ = logger.info(s"Generated Ollama response: $ollamaResponse")

          // Record this conversation turn
          turn = ConversationTurn(
            query = currentQuery,
            bedrockResponse = bedrockResponse,
            ollamaResponse = ollamaResponse,
            timestamp = System.currentTimeMillis()
          )
          _ = {
            conversationHistory = turn :: conversationHistory
            //logger.debug(s"Recorded conversation turn: $turn")
          }

          // Format next query and continue conversation
          nextQuery = ollamaService.formatQueryForBedrock(ollamaResponse)
          result <- conversationLoop(nextQuery, turnCount + 1)
        } yield result
      }
    }

    conversationLoop(initialQuery)
  }

  def getConversationHistory: List[ConversationTurn] = conversationHistory.reverse
}