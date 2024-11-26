package lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.apache.ApacheHttpClient
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.core.JsonProcessingException
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import scala.util.{Try, Success, Failure}

class BedrockLambdaHandler extends RequestHandler[java.util.Map[String, Object], java.util.Map[String, Object]] {
  private val mapper = new ObjectMapper()

  private val httpClient = ApacheHttpClient.builder()
    .build()

  private val bedrockClient = BedrockRuntimeClient.builder()
    .region(Region.US_EAST_1)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .httpClient(httpClient)
    .build()

  override def handleRequest(input: java.util.Map[String, Object], context: Context): java.util.Map[String, Object] = {
    val logger = context.getLogger()
    logger.log(s"Received input: $input")

    try {
      // Extract and parse the request body
      val requestBody = input.get("body").toString
      logger.log(s"Request body: $requestBody")

      // Parse the text field from the request
      val jsonNode = mapper.readTree(requestBody)
      val text = Option(jsonNode.get("text"))
        .map(_.asText())
        .getOrElse(throw new IllegalArgumentException("No text provided in request"))

      logger.log(s"Extracted text: $text")

      // Create Bedrock request payload
      val bedrockRequestJson = mapper.createObjectNode()
      bedrockRequestJson.put("inputText", text)

      val configNode = bedrockRequestJson.putObject("textGenerationConfig")
      configNode.put("temperature", 0.7)
      configNode.put("topP", 0.9)
      configNode.put("maxTokenCount", 512)
      configNode.put("stopSequences", mapper.createArrayNode())

      val bedrockRequest = InvokeModelRequest.builder()
        .modelId("amazon.titan-text-lite-v1")
        .contentType("application/json")
        .accept("application/json")
        .body(SdkBytes.fromUtf8String(bedrockRequestJson.toString))
        .build()

      val bedrockResponse = bedrockClient.invokeModel(bedrockRequest)
      val responseJson = mapper.readTree(bedrockResponse.body().asByteArray())

      val generatedText = Option(responseJson.get("results"))
        .filter(_.isArray)
        .filter(_.size > 0)
        .map(_.get(0).get("outputText").asText())
        .getOrElse("No response generated")

      logger.log(s"Generated text: $generatedText")

      // Create the response JSON
      val responseBody = mapper.createObjectNode()
      responseBody.put("text", generatedText)

      // Convert the response body to a string
      val responseBodyString = mapper.writeValueAsString(responseBody)

      // Create headers
      val headers = new java.util.HashMap[String, String]()
      headers.put("Content-Type", "application/json")
      headers.put("Access-Control-Allow-Origin", "*")

      // Create API Gateway response
      val response = new java.util.HashMap[String, Object]()
      response.put("statusCode", Integer.valueOf(200))
      response.put("headers", headers)
      response.put("body", responseBodyString)
      response.put("isBase64Encoded", java.lang.Boolean.FALSE)

      response

    } catch {
      case e: JsonProcessingException =>
        logger.log(s"Error parsing JSON: ${e.getMessage}")
        createErrorResponse(400, "Invalid request format")

      case e: IllegalArgumentException =>
        logger.log(s"Invalid input: ${e.getMessage}")
        createErrorResponse(400, e.getMessage)

      case e: Exception =>
        logger.log(s"Error processing request: ${e.getMessage}")
        e.printStackTrace()
        createErrorResponse(500, s"Internal server error: ${e.getMessage}")
    } finally {
      try {
        httpClient.close()
      } catch {
        case _: Exception => // Ignore cleanup errors
      }
    }
  }

  private def createErrorResponse(statusCode: Int, message: String): java.util.Map[String, Object] = {
    val headers = new java.util.HashMap[String, String]()
    headers.put("Content-Type", "application/json")
    headers.put("Access-Control-Allow-Origin", "*")

    val errorBody = mapper.createObjectNode()
    errorBody.put("error", message)

    val response = new java.util.HashMap[String, Object]()
    response.put("statusCode", Integer.valueOf(statusCode))
    response.put("headers", headers)
    response.put("body", mapper.writeValueAsString(errorBody))
    response.put("isBase64Encoded", java.lang.Boolean.FALSE)

    response
  }
}
