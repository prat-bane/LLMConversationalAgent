package service

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import software.amazon.awssdk.core.SdkBytes
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ObjectNode, ArrayNode}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

class BedrockService(config: Config)(implicit ec: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val mapper = new ObjectMapper()

  private val credentials = AwsBasicCredentials.create(
    config.getString("aws.accessKeyId"),
    config.getString("aws.secretAccessKey")
  )

  private val bedrockClient = BedrockRuntimeClient.builder()
    .region(Region.of(config.getString("aws.region")))
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .build()

  def generateText(prompt: String): Future[String] = Future {
    try {
      // Create the request body according to Titan's schema
      val requestBody: ObjectNode = mapper.createObjectNode()
      requestBody.put("inputText", prompt)

      val configNode = mapper.createObjectNode()
      configNode.put("temperature", 0.7)
      configNode.put("topP", 0.9)
      configNode.put("maxTokenCount", 512)
      configNode.put("stopSequences", mapper.createArrayNode())

      requestBody.set("textGenerationConfig", configNode)

      logger.info(s"Request body: ${requestBody.toString}")

      // Convert request to bytes
      val requestBytes = SdkBytes.fromUtf8String(requestBody.toString)

      // Create the request
      val request = InvokeModelRequest.builder()
        .modelId("amazon.titan-text-lite-v1")
        .contentType("application/json")
        .accept("application/json")
        .body(requestBytes)
        .build()

      // Invoke the model
      val response = bedrockClient.invokeModel(request)

      // Parse response
      val responseJson = mapper.readTree(response.body().asByteArray())
      val results = responseJson.get("results")
      if (results != null && results.isArray && results.size() > 0) {
        results.get(0).get("outputText").asText()
      } else {
        "No response generated"
      }

    } catch {
      case e: Exception =>
        logger.error(s"Error generating text from Bedrock: ${e.getMessage}", e)
        throw e
    }
  }

  def shutdown(): Unit = {
    bedrockClient.close()
  }
}