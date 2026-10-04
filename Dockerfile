# syntax=docker/dockerfile:1

# ---- build: resolve dependencies first so that code-only changes reuse the cached layer
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# tests need Docker (Testcontainers) and run in CI instead
RUN ./mvnw -B -q -DskipTests -Djacoco.skip=true package

# ---- runtime: JRE only, non-root
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/medislot-*.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
