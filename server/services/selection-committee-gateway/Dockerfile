# ── Build stage ──────────────────────────────────────────
FROM gradle:jdk21-ubi-minimal@sha256:41607448ecfb9d51c68f4f5db84590aef1009cc196aff12a4c20eae273c50d74 AS build

RUN groupadd -g 10001 builder && \
    useradd  -u 10000 -g builder -m builder

WORKDIR /app

ENV GRADLE_USER_HOME=/app/.gradle
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Dorg.gradle.configuration-cache=true"

# 1 ─ Copy build scripts (layer-cached unless build config changes)
COPY --chown=builder:builder buildSrc/ buildSrc/
COPY --chown=builder:builder gradle/   gradle/
COPY --chown=builder:builder settings.gradle build.gradle ./

# 2 ─ Pre-warm dependencies
RUN --mount=type=cache,target=/app/.gradle,uid=10000,gid=10001 \
    gradle dependencies --no-daemon || true

# 3 ─ Copy sources & build
COPY --chown=builder:builder src/ src/
RUN --mount=type=cache,target=/app/.gradle,uid=10000,gid=10001 \
    gradle bootJar --no-daemon

# ── Runtime stage ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine@sha256:8728e354e012e18310faa7f364d00185277dec741f4f6d593af6c61fc0eb15fd

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-local}
ENV SERVER_PORT=${SERVER_PORT:-8080}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+HeapDumpOnOutOfMemoryError", "-jar", "app.jar"]
