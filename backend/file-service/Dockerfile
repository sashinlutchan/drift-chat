# file-service — multi-stage build (spec section 24).
# Built from the repository root so the shared backend/proto folder is available:
#   docker build -f backend/file-service/Dockerfile -t drift/file-service .

# ── Build: Amazon Corretto 25 (glibc, so the protoc binary runs) + the service's own Gradle Wrapper ───────────
FROM amazoncorretto:25 AS build
# The Gradle wrapper script needs xargs
RUN yum install -y findutils && yum clean all
WORKDIR /workspace/backend
COPY backend/proto ./proto
WORKDIR /workspace/backend/file-service
COPY backend/file-service/gradlew backend/file-service/gradlew.bat backend/file-service/settings.gradle.kts backend/file-service/build.gradle.kts backend/file-service/gradle.properties backend/file-service/detekt.yml ./
COPY backend/file-service/gradle ./gradle
# Dependency-cache layer: resolved before the sources are copied so code changes don't re-download everything
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/file-service/src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination /workspace/extracted

# ── Run: slim Corretto 25 Alpine, non-root, Spring Boot layered jar ───────────────────────────────────────────
FROM amazoncorretto:25-alpine AS runtime
RUN addgroup -S drift && adduser -S drift -G drift
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER drift
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8084 9084 8184
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
