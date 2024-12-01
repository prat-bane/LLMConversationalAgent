# LLM Conversational Agent Sever using AWS Bedrock and Ollama

### Author : Pratyay Banerjee
### Email : pbane8@uic.edu

# LLM Server Implementation

A streamlined Scala-based implementation of a text generation server using AWS Bedrock (Titan) through API Gateway and Lambda, built with Akka HTTP.


Related Applications

LLM Conversational Agent Client - Client application that interacts with this server

## System Flow

![System Architecture](System%20Flow.png)

The system architecture diagram shows the flow of requests through the dockerized components and AWS services. The numbers indicate the sequence of operations:

Query: Client sends request to EC2 Server
Forward: EC2 forwards to API Gateway
Invoke: API Gateway triggers Lambda
Generate: Lambda calls Bedrock
Response: Bedrock returns generated text
Return: Response flows back through Lambda
Response: API Gateway returns to EC2
Response: EC2 returns to Client
Process: Client processes with Ollama
Next Query: New query generated
1. **Client Request**: 
   - Client sends a POST request to `/api/v1/chat` with a query
   - Request is handled by Akka HTTP server

2. **Server Processing**:
   - `LLMRoutes` receives the request and validates the input
   - Query is passed to `LLMService`
   - `APIGatewayService` prepares the gRPC request

3. **AWS Integration**:
   - Request is sent to API Gateway
   - Lambda function is triggered
   - Lambda calls Bedrock for text generation
   - Response is returned through the same path


## Prerequisites

- Scala 2.13.10
- Amazon Corretto JDK 11
- SBT
- AWS Account with Bedrock access
- AWS CLI configured with appropriate credentials

## Technologies Used

- Akka HTTP for REST API
- AWS Bedrock (Amazon Titan Lite model) for text generation
- AWS Lambda for serverless computing
- gRPC for Lambda communication
- Typesafe Config for configuration management
- SLF4J with Logback for logging

## Project Structure

```
src/
├── main/
│   ├── scala/
│   │   ├── service/
│   │   │   ├── APIGatewayService.scala    # AWS API Gateway interaction
│   │   │   └── LLMService.scala           # Core LLM service implementation
│   │   ├── lambda/
│   │   │   └── BedrockLambdaHandler.scala # AWS Lambda handler
│   │   └── Main.scala                     # Application entry point
│   └── resources/
│       ├── application.conf               # Configuration file
│       └── logback.xml                    # Logging configuration
```




## Configuration

Create `src/main/resources/application.conf`:

```hocon
http {
  interface = "0.0.0.0"
  port = 8080
}

llm {
  maxResponseLength = 1000
  timeout = 30s
}

bedrock {
  model {
    id = "amazon.titan-text-lite-v1"
    config {
      temperature = 0.7
      topP = 0.9
      maxTokenCount = 50
    }
  }
  http {
    maxConnections = 50
  }
}

api {
  prefix = "api"
  version = "v1"
  endpoints {
    chat = "chat"
    health = "health"
  }
}

conversation {
  max-turns = 5  # Maximum number of back-and-forth turns in a conversation
}

aws {
  accessKeyId = ""
  secretAccessKey = ""
  region = "us-east-1"
}
api {
  gateway {
    url = "https://qb1jxkhcy5.execute-api.us-east-1.amazonaws.com/prod"
    timeout = 30s
  }
  }

akka {
  version = "2.7.0"
  loglevel = "INFO"
  stdout-loglevel = "INFO"
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"

  http {
    server {
      idle-timeout = 60s
      request-timeout = 30s
    }
  }
}
```

## API Endpoints

### Generate Text
```http
POST /api/v1/chat
Content-Type: application/json

{
    "text": "Your query here"
}
```

Response:
```json
{
    "text": "Generated response from Bedrock"
}
```


## Building and Running

1. Build the project:
```bash
sbt clean compile
```

2. Run the tests:
```bash
sbt test
```

3. Run the server:
```bash
sbt run
```

## AWS Lambda Setup

1. Create a new Lambda function using the provided `BedrockLambdaHandler.scala`
2. Configure Lambda with appropriate IAM roles for Bedrock access
3. Set up an API Gateway trigger for the Lambda function
4. Update the `api.gateway.url` in your configuration

## Error Handling

The server implements basic error handling:
- Invalid requests return 400 Bad Request
- Server errors return 500 Internal Server Error
- All errors are logged with stack traces
- Lambda timeout and Bedrock errors are properly handled


