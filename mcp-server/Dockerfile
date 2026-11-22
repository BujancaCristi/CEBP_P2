# MCP server. Multi-stage: Maven build -> JRE 21 runtime.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 app
COPY --from=build /build/target/mcp-server-*.jar app.jar
USER app
EXPOSE 8083
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 \
  CMD wget -qO- http://localhost:8083/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
