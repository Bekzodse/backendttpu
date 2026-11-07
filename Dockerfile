# =============================================================================
# Stage 1: Build Stage (Java 21 + Gradle Kotlin DSL)
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build configuration first for optimal layer caching
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies || true

# Copy application source and Flyway migrations
COPY src ./src

# Build the executable Spring Boot JAR (skip tests during image build; CI runs tests)
RUN ./gradlew --no-daemon bootJar -x test

# =============================================================================
# Stage 2: Production Runtime Stage (Java 21 JRE Alpine, Non-Root User)
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="TTPU Raqamli Inventar Backend API" \
      org.opencontainers.image.description="Turin Polytechnic University in Tashkent — Digital Inventory Modular Monolith API" \
      org.opencontainers.image.version="1.0.0"

# Install curl and tzdata for container healthchecks and UTC timezone enforcement
RUN apk add --no-cache curl tzdata && \
    addgroup -S ttpu && \
    adduser -S ttpu -G ttpu

ENV TZ=UTC \
    SERVER_PORT=8080 \
    DB_URL=jdbc:postgresql://postgres:5432/ttpu_inventory \
    DB_USERNAME=ttpu \
    DB_PASSWORD=ttpu_secret \
    DB_POOL_MAX_SIZE=30 \
    DB_POOL_MIN_IDLE=5 \
    MAIL_HOST=localhost \
    MAIL_PORT=1025 \
    MAIL_HEALTH_ENABLED=false \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC -Dfile.encoding=UTF-8"

WORKDIR /app

COPY --from=builder --chown=ttpu:ttpu /workspace/build/libs/app.jar /app/app.jar

USER ttpu

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
