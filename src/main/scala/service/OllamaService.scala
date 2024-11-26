package service

import com.typesafe.config.Config
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.OllamaResult
import io.github.ollama4j.utils.Options
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class OllamaService(config: Config)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val ollamaAPI = new OllamaAPI(config.getString("ollama.host"))
  ollamaAPI.setRequestTimeoutSeconds(config.getInt("ollama.request-timeout-seconds"))
  private val model = config.getString("ollama.model")
  private val maxTurns = config.getInt("conversation.max-turns")
  private val options = new Options(Map[String, AnyRef]().asJava)

  def generateNextQuery(previousResponse: String): Future[String] = {
    Future {
      logger.info(s"Generating next query based on response: $previousResponse")
      val prompt = s"how can you respond to the statement: $previousResponse"

      try {
        val result = ollamaAPI.generate(model, prompt, false, options)
        val nextQuery = result.getResponse
        logger.info(s"Generated next query: $nextQuery")
        nextQuery
      } catch {
        case e: Exception =>
          logger.error(s"Error generating next query: ${e.getMessage}", e)
          throw e
      }
    }
  }

  def formatQueryForBedrock(ollamaResponse: String): String = {
    s"Do you have any comments on: $ollamaResponse"
  }

  def shouldContinueConversation(turnCount: Int): Boolean = {
    turnCount < maxTurns
  }

  def shutdown(): Unit = {
    logger.info("Shutting down Ollama Service")
  }
}