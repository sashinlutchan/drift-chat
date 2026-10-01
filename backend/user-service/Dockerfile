
FROM amazoncorretto:25 AS build
RUN yum install -y findutils && yum clean all
WORKDIR /workspace/backend
COPY backend/proto ./proto
WORKDIR /workspace/backend/user-service
COPY backend/user-service/gradlew backend/user-service/gradlew.bat backend/user-service/settings.gradle.kts backend/user-service/build.gradle.kts backend/user-service/gradle.properties backend/user-service/detekt.yml ./
COPY backend/user-service/gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/user-service/src ./src
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
EXPOSE 8081 9081 8181
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
