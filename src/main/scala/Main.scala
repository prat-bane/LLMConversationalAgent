import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import service.{LLMRoutes, LLMService}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main {

  private val logger = LoggerFactory.getLogger(this.getClass)
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "llm-service")
    implicit val executionContext: ExecutionContext = system.executionContext

    val config = ConfigFactory.load()
    val interface = config.getString("http.interface")
    val port = config.getInt("http.port")

    val llmService = new LLMService(config)
    val routes = new LLMRoutes(llmService)

    val bindingFuture = Http().newServerAt(interface, port).bind(routes.routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"Server online at http://${address.getHostString}:${address.getPort}/")

        sys.addShutdownHook {
          logger.info("Shutdown hook triggered")
          binding.unbind()
          llmService.shutdown()
          system.terminate()
          logger.info("Server shutdown initiated")
        }

      case Failure(ex) =>
        logger.error(s"Failed to bind to $interface:$port!", ex)
        llmService.shutdown()
        system.terminate()
    }

    // Keep the main thread alive
    Thread.currentThread().join()
  }
}
