
FROM amazoncorretto:25 AS build
RUN yum install -y findutils && yum clean all
WORKDIR /workspace/backend
COPY backend/proto ./proto
WORKDIR /workspace/backend/message-service
COPY backend/message-service/gradlew backend/message-service/gradlew.bat backend/message-service/settings.gradle.kts backend/message-service/build.gradle.kts backend/message-service/gradle.properties backend/message-service/detekt.yml ./
COPY backend/message-service/gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/message-service/src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination /workspace/extracted

FROM amazoncorretto:25-alpine AS runtime
RUN addgroup -S drift && adduser -S drift -G drift
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER drift
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8083 8090 8183
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
