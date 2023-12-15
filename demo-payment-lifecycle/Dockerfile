# ─── Stage 1: Build ─────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy parent POM first for dependency resolution
COPY pom.xml ./

# Copy all module POMs to establish reactor structure
COPY flowcore-api/pom.xml                flowcore-api/pom.xml
COPY flowcore-runtime/pom.xml            flowcore-runtime/pom.xml
COPY flowcore-statemachine/pom.xml       flowcore-statemachine/pom.xml
COPY flowcore-transaction/pom.xml        flowcore-transaction/pom.xml
COPY flowcore-kafka/pom.xml              flowcore-kafka/pom.xml
COPY flowcore-observability/pom.xml      flowcore-observability/pom.xml
COPY flowcore-security/pom.xml           flowcore-security/pom.xml
COPY flowcore-integrations/pom.xml       flowcore-integrations/pom.xml
COPY flowcore-starter/pom.xml            flowcore-starter/pom.xml
COPY demo-payment-lifecycle/pom.xml      demo-payment-lifecycle/pom.xml

# Download dependencies (layer caching — re-run only when POMs change)
RUN mvn dependency:go-offline -B -am -pl demo-payment-lifecycle

# Copy full source code
COPY flowcore-api/                flowcore-api/
COPY flowcore-runtime/            flowcore-runtime/
COPY flowcore-statemachine/       flowcore-statemachine/
COPY flowcore-transaction/        flowcore-transaction/
COPY flowcore-kafka/              flowcore-kafka/
COPY flowcore-observability/      flowcore-observability/
COPY flowcore-security/           flowcore-security/
COPY flowcore-integrations/       flowcore-integrations/
COPY flowcore-starter/            flowcore-starter/
COPY demo-payment-lifecycle/      demo-payment-lifecycle/

# Build the application with all required dependencies (-am = also make)
RUN mvn package -B -am -pl demo-payment-lifecycle -DskipTests

# ─── Stage 2: Runtime ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser

# Copy the built JAR from the build stage
COPY --from=build /workspace/demo-payment-lifecycle/target/*.jar app.jar

# Create log directory
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

USER appuser

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-jar", "app.jar", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom"]
