FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for health check if needed
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy the built Spring Boot fat JAR
COPY target/aives-backend-*.jar app.jar

EXPOSE 8080

# Run Spring Boot
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

