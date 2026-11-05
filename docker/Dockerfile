FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon --console=plain --max-workers=2 -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=512m" -Pkotlin.compiler.execution.strategy=in-process bootJar -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ai-ex && adduser -S -G ai-ex ai-ex
WORKDIR /app
ARG MODULE=app
COPY --from=build --chown=ai-ex:ai-ex /workspace/${MODULE}/build/libs/service.jar /app/service.jar
USER ai-ex
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar service.jar"]
