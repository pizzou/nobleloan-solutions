# ============================================================
# FINOS / LOANSAAS PRO - PRODUCTION BACKEND IMAGE
# AOS RWANDA DEPLOYMENT
# ============================================================

# ------------------------------------------------------------
# BUILD STAGE
# ------------------------------------------------------------

FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven descriptor first for Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn -B dependency:go-offline

# Copy application source
COPY src ./src

# Build and test the application
RUN mvn -B clean verify


# ------------------------------------------------------------
# RUNTIME STAGE
# ------------------------------------------------------------

FROM eclipse-temurin:21-jre

WORKDIR /app

# ------------------------------------------------------------
# NON-ROOT APPLICATION USER
# ------------------------------------------------------------

RUN groupadd --system loansaas \
    && useradd --system \
       --gid loansaas \
       --home-dir /app \
       --no-create-home \
       loansaas

# Application/log directories
RUN mkdir -p /app/logs \
    && chown -R loansaas:loansaas /app

# ------------------------------------------------------------
# APPLICATION JAR
# ------------------------------------------------------------

COPY --from=builder /app/target/*.jar /app/app.jar

RUN chown loansaas:loansaas /app/app.jar

USER loansaas

# ------------------------------------------------------------
# SERVER PORT
# ------------------------------------------------------------

EXPOSE 8080


# ------------------------------------------------------------
# CONTAINER HEALTH CHECK
# ------------------------------------------------------------
#
# IMPORTANT:
#
# Do not use the aggregate /actuator/health endpoint here if
# financial reconciliation contributes to its status.
#
# Reconciliation failures must be monitored separately and
# must not automatically make the application container
# appear unavailable.
#
# Readiness answers:
#
# "Can this instance receive application traffic?"
#
# ------------------------------------------------------------

HEALTHCHECK \
    --interval=30s \
    --timeout=10s \
    --start-period=90s \
    --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness \
        || exit 1


# ------------------------------------------------------------
# JVM CONFIGURATION
# ------------------------------------------------------------

ENTRYPOINT [
    "java",
    "-XX:+UseContainerSupport",
    "-XX:MaxRAMPercentage=75.0",
    "-XX:InitialRAMPercentage=25.0",
    "-XX:+UseG1GC",
    "-XX:+ExitOnOutOfMemoryError",
    "-Djava.security.egd=file:/dev/urandom",
    "-jar",
    "/app/app.jar"
]

STOPSIGNAL SIGTERM