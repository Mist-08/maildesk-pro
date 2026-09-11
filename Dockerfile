# MailDesk Pro - imagen reproducible (construcción en dos etapas)
# Requisitos: Docker 24+ (o compatible). No requiere JDK ni Maven en el host.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
ENV TZ=America/Mexico_City \
    JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    APP_STORAGE_DIR=/data/storage \
    APP_OTP_HMAC_KEY_FILE=/data/otp-hmac.key
RUN useradd --system --create-home --uid 10001 maildesk \
    && mkdir -p /data/storage && chown -R maildesk:maildesk /data
WORKDIR /app
COPY --from=build /workspace/target/maildesk-pro-*.jar /app/app.jar
USER maildesk
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
