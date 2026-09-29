# room-service — multi-stage build (spec section 24).
# Built from the repository root so the shared backend/proto folder is available:
#   docker build -f backend/room-service/Dockerfile -t drift/room-service .

# ── Build: Amazon Corretto 25 (glibc, so the protoc binary runs) + the service's own Gradle Wrapper ───────────
FROM amazoncorretto:25 AS build
# The Gradle wrapper script needs xargs
RUN yum install -y findutils && yum clean all
WORKDIR /workspace/backend
COPY backend/proto ./proto
WORKDIR /workspace/backend/room-service
COPY backend/room-service/gradlew backend/room-service/gradlew.bat backend/room-service/settings.gradle.kts backend/room-service/build.gradle.kts backend/room-service/gradle.properties backend/room-service/detekt.yml ./
COPY backend/room-service/gradle ./gradle
# Dependency-cache layer: resolved before the sources are copied so code changes don't re-download everything
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY backend/room-service/src ./src
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
EXPOSE 8082 9082 8182
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
