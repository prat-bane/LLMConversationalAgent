# Stage 1: Build the application
FROM mozilla/sbt:latest AS builder

WORKDIR /app

# Copy project configuration files first to cache dependencies
COPY project ./project
COPY build.sbt ./

# Cache SBT dependencies
RUN sbt update

# Copy source code
COPY src ./src

# Build the application
RUN sbt clean compile assembly

# Stage 2: Run the application
FROM openjdk:11-jre-slim

WORKDIR /app

# Create directory for resources
RUN mkdir -p /app/resources

# Copy the application configuration
COPY src/main/resources/application.conf ./application.conf

# Copy the assembled jar from the builder stage
COPY --from=builder /app/target/scala-2.13/*assembly*.jar ./app.jar

EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]