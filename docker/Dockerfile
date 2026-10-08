FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon --console=plain \
    :config-server:bootJar :discovery-server:bootJar :gateway:bootJar \
    :account-service:bootJar :persona-service:bootJar :dialog-service:bootJar \
    :care-service:bootJar :notification-service:bootJar

ARG SERVICE
RUN cp "${SERVICE}/build/libs/${SERVICE}.jar" /workspace/service.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ai-ex && adduser -S -G ai-ex ai-ex
WORKDIR /app
COPY --from=build --chown=ai-ex:ai-ex /workspace/service.jar service.jar
USER ai-ex
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=12 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar service.jar"]
