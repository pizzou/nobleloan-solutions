# ============================================================
# LOANSAAS PRO - PRODUCTION DOCKER IMAGE
# ============================================================

# ------------------------------------------------------------
# BUILD STAGE
# ------------------------------------------------------------

FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -q

COPY src ./src

RUN mvn clean package -DskipTests -q


# ------------------------------------------------------------
# RUNTIME STAGE
# ------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ------------------------------------------------------------
# NON-ROOT USER
# ------------------------------------------------------------

RUN addgroup -S loansaas \
    && adduser -S loansaas -G loansaas

RUN mkdir -p /var/log/loansaas \
    && chown -R loansaas:loansaas /var/log/loansaas

COPY --from=builder /app/target/*.jar app.jar

RUN chown loansaas:loansaas /app/app.jar

USER loansaas

EXPOSE 8080


# ------------------------------------------------------------
# CONTAINER HEALTH CHECK
# ------------------------------------------------------------
#
# IMPORTANT:
#
# Do NOT use /actuator/health here.
#
# The aggregate health endpoint includes financial
# reconciliation health. A reconciliation failure must
# generate an alert but MUST NOT make the entire application
# unavailable.
#
# Liveness answers:
#
# "Is the JVM/application process alive?"
#
# Readiness answers:
#
# "Can this instance receive traffic?"
#
# Financial reconciliation is monitored separately.
#

HEALTHCHECK \
    --interval=30s \
    --timeout=10s \
    --start-period=90s \
    --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness \
        || exit 1


# ------------------------------------------------------------
# JVM
# ------------------------------------------------------------

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", \
  "app.jar"]