# Build context: forum-cloud
# docker build -f forum-media-service/Dockerfile -t forum-media-service:latest .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl forum-media-service -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
ARG SKYWALKING_AGENT_VERSION=9.2.0
ARG SKYWALKING_AGENT_BASE_URL=https://archive.apache.org/dist/skywalking/java-agent

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SW_AGENT_NAME=forum-media-service \
    SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800 \
    JAVA_TOOL_OPTIONS="-javaagent:/skywalking/skywalking-agent.jar" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# SkyWalking Java Agent: override SKYWALKING_AGENT_VERSION/BASE_URL for an internal mirror.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && mkdir -p /skywalking \
 && curl -fsSL "${SKYWALKING_AGENT_BASE_URL}/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" -o /tmp/skywalking-agent.tgz \
 && tar -xzf /tmp/skywalking-agent.tgz -C /skywalking --strip-components=1 \
 && test -f /skywalking/skywalking-agent.jar \
 && rm -f /tmp/skywalking-agent.tgz \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system forum \
 && useradd --system --gid forum --home-dir /app --shell /usr/sbin/nologin forum \
 && chown -R forum:forum /skywalking

WORKDIR /app
COPY --from=build /workspace/forum-media-service/target/forum-media-service.jar /app/app.jar
RUN chown -R forum:forum /app
USER forum

EXPOSE 8085 9085
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:9085/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
