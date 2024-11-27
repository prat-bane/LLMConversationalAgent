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
  // Create options with token limits and other parameters
  private val options = new Options(Map[String, AnyRef](
    "num_predict" -> Integer.valueOf(50),  // Maximum number of tokens to generate
    "temperature" -> java.lang.Float.valueOf(0.7f),  // Controls randomness (0.0-1.0)
    "top_k" -> Integer.valueOf(40),  // Limits vocabulary to top K tokens
    "top_p" -> java.lang.Float.valueOf(0.9f)  // Nucleus sampling threshold
  ).asJava)

  def generateNextQuery(previousResponse: String): Future[String] = {
    Future {
      logger.info(s"Generating next query based on response: $previousResponse")
      val prompt = previousResponse

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
    ollamaResponse
  }

  def shouldContinueConversation(turnCount: Int): Boolean = {
    turnCount < maxTurns
  }

  def shutdown(): Unit = {
    logger.info("Shutting down Ollama Service")
  }
}