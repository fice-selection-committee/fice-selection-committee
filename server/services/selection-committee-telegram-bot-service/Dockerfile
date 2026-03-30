# ── Stage 1: Build shared libraries ─────────────────────
FROM eclipse-temurin:21-jdk-alpine AS lib-builder
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts version.properties ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/
COPY libs/ libs/

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && GITHUB_REF_TYPE=tag ./gradlew publishToMavenLocal --no-daemon

# ── Stage 2: Build service ──────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS service-builder
WORKDIR /workspace

COPY --from=lib-builder /root/.m2/repository/ /root/.m2/repository/
COPY --from=lib-builder /root/.gradle/ /root/.gradle/
COPY services/selection-committee-telegram-bot-service/ ./

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test -x integrationTest

# ── Stage 3: Runtime ────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 10001 -S spring && \
    adduser -u 10000 -S -G spring spring && \
    chown -R spring:spring /app

USER spring:spring

COPY --from=service-builder /workspace/build/libs/*.jar app.jar

EXPOSE 8087

HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=30s \
  CMD wget -q --spider http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=100", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-jar", "app.jar"]
