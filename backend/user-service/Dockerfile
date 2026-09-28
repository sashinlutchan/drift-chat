# user-service — multi-stage build (spec section 24)
# Build: Amazon Corretto 25 + the service's own Gradle Wrapper
FROM amazoncorretto:25.0.1-alpine3.22 AS build
WORKDIR /workspace

# Copy the whole service (standalone Gradle project) — deps layer first for caching
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties detekt.yml ./
COPY gradle ./gradle
COPY src ./src

# Dependency-cache layer: resolve everything before compiling
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
RUN ./gradlew --no-daemon bootJar

# Runtime: slim Corretto Alpine, non-root, layered jar extracted
FROM amazoncorretto:25.0.1-alpine3.22 AS runtime
RUN addgroup -S drift && adduser -S drift -G drift
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted && rm app.jar

USER drift
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8081 9081 8181
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar extracted/app.jar"]
