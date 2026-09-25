FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY config config
COPY app/build.gradle.kts app/
RUN ./gradlew --no-daemon --console=plain -q :app:dependencies --configuration runtimeClasspath > /dev/null

COPY . .
RUN ./gradlew --no-daemon --console=plain :app:bootJar -x test \
 && java -Djarmode=tools -jar app/build/libs/ai-ex.jar extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ai-ex && adduser -S -G ai-ex ai-ex
WORKDIR /app
COPY --from=build --chown=ai-ex:ai-ex /workspace/extracted/dependencies/ ./
COPY --from=build --chown=ai-ex:ai-ex /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=ai-ex:ai-ex /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=ai-ex:ai-ex /workspace/extracted/application/ ./
USER ai-ex
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar ai-ex.jar"]
