package lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.SdkHttpClient
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.core.JsonProcessingException
import com.typesafe.config.ConfigFactory
import gRPCService.lambda.{ResponseMetadata, TextGenerationRequest, TextGenerationResponse}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region

import java.util.Base64
import scala.util.{Failure, Success, Try}

class BedrockLambdaHandler extends RequestHandler[java.util.Map[String, Object], java.util.Map[String, Object]] {
  private val mapper = new ObjectMapper()
  private val config = ConfigFactory.load()

  private lazy val sdkHttpClient: SdkHttpClient = {
    ApacheHttpClient.builder()
      .maxConnections(config.getInt("bedrock.http.maxConnections"))
      .build()
  }

  private lazy val bedrockClient: BedrockRuntimeClient = {
    BedrockRuntimeClient.builder()
      .region(Region.US_EAST_1)
      .credentialsProvider(DefaultCredentialsProvider.create())
      .httpClient(sdkHttpClient)
      .build()
  }

  override def handleRequest(input: java.util.Map[String, Object], context: Context): java.util.Map[String, Object] = {
    val logger = context.getLogger()
    logger.log(s"Received input: $input")

    try {
      // Get base64 encoded protobuf from request body and decode it
      val requestBody = input.get("body").toString
      val protoBytes = Base64.getDecoder.decode(requestBody)

      // Parse the Protocol Buffer request
      val request = TextGenerationRequest.parseFrom(protoBytes)
      val text = request.text

      logger.log(s"Decoded protobuf request text: $text")

      // Create Bedrock request payload
      val bedrockRequestJson = mapper.createObjectNode()
      bedrockRequestJson.put("inputText", text)

      val configNode = bedrockRequestJson.putObject("textGenerationConfig")
      configNode.put("temperature", config.getDouble("bedrock.model.config.temperature"))
      configNode.put("topP", config.getDouble("bedrock.model.config.topP"))
      configNode.put("maxTokenCount", config.getInt("bedrock.model.config.maxTokenCount"))
      configNode.put("stopSequences", mapper.createArrayNode())

      val startTime = System.currentTimeMillis()

      val bedrockRequest = InvokeModelRequest.builder()
        .modelId("amazon.titan-text-lite-v1")
        .contentType("application/json")
        .accept("application/json")
        .body(SdkBytes.fromUtf8String(bedrockRequestJson.toString))
        .build()

      val bedrockResponse = bedrockClient.invokeModel(bedrockRequest)
      val processingTime = System.currentTimeMillis() - startTime

      val responseJson = mapper.readTree(bedrockResponse.body().asByteArray())

      val generatedText = Option(responseJson.get("results"))
        .filter(_.isArray)
        .filter(_.size > 0)
        .map(_.get(0).get("outputText").asText())
        .getOrElse("No response generated")

      logger.log(s"Generated text: $generatedText")

      // Create Protocol Buffer response
      val response = TextGenerationResponse(
        generatedText = generatedText,
        metadata = Some(ResponseMetadata(
          timestamp = System.currentTimeMillis(),
          queryLength = text.length,
          responseLength = generatedText.length,
          processingTimeMs = processingTime
        ))
      )

      // Serialize the Protocol Buffer response to bytes and encode as base64
      val responseBytes = response.toByteArray
      val responseBase64 = Base64.getEncoder.encodeToString(responseBytes)

      // Create headers with gRPC content type
      val headers = new java.util.HashMap[String, String]()
      headers.put("Content-Type", "application/grpc+proto")
      headers.put("Accept", "application/grpc+proto")
      headers.put("Access-Control-Allow-Origin", "*")

      // Create API Gateway response
      val apiResponse = new java.util.HashMap[String, Object]()
      apiResponse.put("statusCode", Integer.valueOf(200))
      apiResponse.put("headers", headers)
      apiResponse.put("body", responseBase64)
      apiResponse.put("isBase64Encoded", java.lang.Boolean.TRUE)

      apiResponse

    } catch {
      case e: com.google.protobuf.InvalidProtocolBufferException =>
        logger.log(s"Error parsing protobuf: ${e.getMessage}")
        createErrorResponse(400, "Invalid protobuf format")

      case e: IllegalArgumentException =>
        logger.log(s"Invalid input: ${e.getMessage}")
        createErrorResponse(400, e.getMessage)

      case e: Exception =>
        logger.log(s"Error processing request: ${e.getMessage}")
        e.printStackTrace()
        createErrorResponse(500, s"Internal server error: ${e.getMessage}")
    }
  }

  private def createErrorResponse(statusCode: Int, message: String): java.util.Map[String, Object] = {
    // Create error response using Protocol Buffer
    val errorResponse = TextGenerationResponse(
      generatedText = message,
      metadata = Some(ResponseMetadata(
        timestamp = System.currentTimeMillis(),
        queryLength = 0,
        responseLength = message.length,
        processingTimeMs = 0
      ))
    )

    val errorBytes = errorResponse.toByteArray
    val errorBase64 = Base64.getEncoder.encodeToString(errorBytes)

    val headers = new java.util.HashMap[String, String]()
    headers.put("Content-Type", "application/grpc+proto")
    headers.put("Accept", "application/grpc+proto")
    headers.put("Access-Control-Allow-Origin", "*")

    val response = new java.util.HashMap[String, Object]()
    response.put("statusCode", Integer.valueOf(statusCode))
    response.put("headers", headers)
    response.put("body", errorBase64)
    response.put("isBase64Encoded", java.lang.Boolean.TRUE)

    response
  }
}