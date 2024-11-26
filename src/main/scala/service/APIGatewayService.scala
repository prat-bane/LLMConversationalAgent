package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import spray.json._
import DefaultJsonProtocol._
import gRPCService.lambda.{TextGenerationRequest, TextGenerationResponse}

import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory



class APIGatewayService(config: Config)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val apiGatewayUrl = config.getString("aws.api.gateway.url")

  def generateText(prompt: String)(implicit system: ActorSystem[_]): Future[String] = {
    logger.info(s"Preparing gRPC-like request with prompt: $prompt")

    // Create Protocol Buffer message
    val request = TextGenerationRequest(
      text = prompt
    )

    // Serialize the Protocol Buffer message
    val serializedRequest = request.toByteArray
    logger.debug(s"Serialized request size: ${serializedRequest.length} bytes")

    // Create the request JSON with serialized protobuf as base64
    val requestJson = JsObject(
      "body" -> JsString(java.util.Base64.getEncoder.encodeToString(serializedRequest))
    ).toString()

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = apiGatewayUrl,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        requestJson
      )
    )

    Http()(system)
      .singleRequest(httpRequest)
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { responseBody =>
              logger.info(s"Raw response from API Gateway: $responseBody")

              val parsedResponse = responseBody.parseJson.asJsObject

              // Extract and decode the base64 protobuf response
              val responseBytes = for {
                body <- parsedResponse.fields.get("body")
                bodyStr = body.convertTo[String]
                decoded = java.util.Base64.getDecoder.decode(bodyStr)
              } yield decoded

              responseBytes.map { bytes =>
                try {
                  // Parse the Protocol Buffer response
                  val response = TextGenerationResponse.parseFrom(bytes)
                  response.generatedText
                } catch {
                  case e: Exception =>
                    logger.error(s"Failed to parse protobuf response: ${e.getMessage}")
                    throw new Exception("Failed to parse protobuf response")
                }
              }.getOrElse {
                logger.error(s"Unable to parse response body: $responseBody")
                throw new Exception("Failed to extract response")
              }
            }

          case _ =>
            Unmarshal(response.entity).to[String].flatMap { errorBody =>
              logger.error(s"API Gateway request failed with status ${response.status}, body: $errorBody")
              Future.failed(new RuntimeException(s"API Gateway request failed: ${response.status} - $errorBody"))
            }
        }
      }
  }

  def shutdown(): Unit = {
    logger.info("Shutting down API Gateway Service")
  }
}