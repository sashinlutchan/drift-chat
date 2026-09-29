import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("dev.detekt") version "2.0.0-alpha.3"
    id("com.google.protobuf") version "0.10.0"
    jacoco
}

group = "com.drift"
version = "0.0.1-SNAPSHOT"
description = "user-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.AMAZON
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    // ── Spring Boot starters ────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── Kotlin ──────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // ── Databases & drivers ─────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql")

    // ── gRPC (Spring Boot 4.1 built-in starters) ────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("com.google.protobuf:protobuf-kotlin:4.35.1")

    // ── Scheduling (ShedLock) ───────────────────────────────────────────────
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")

    // ── Testing (unit + integration tests together in src/test/kotlin) ──────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("io.grpc:grpc-testing")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── Tests: unit + integration all run with `test` (part of `check`) ─────────
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// ── JaCoCo (≥ 80% on service + common packages, wired into check) ────────────
jacoco {
    toolVersion = "0.8.14"
}

// Coverage is measured on the service and common packages (spec section 23).
val coverageIncludes = listOf("com/drift/user/service/**", "com/drift/user/common/**")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs.files.map { fileTree(it) { include(coverageIncludes) } }),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData(tasks.test.get())
    sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs.files.map { fileTree(it) { include(coverageIncludes) } }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = 0.80.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.bootJar {
    layered {}
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

// ── Protobuf / gRPC code generation (spec section 16) ───────────────────────
// Shared protos live in backend/proto (../proto locally, ./proto in the Docker build context).
val protoRoot: File = listOf(file("../proto"), file("proto")).first { it.exists() }

sourceSets {
    main {
        proto {
            srcDir(protoRoot)
            include("user/**", "file/**")
        }
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.83.1" }
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:1.5.0:jdk8@jar" }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                maybeCreate("grpc")
                maybeCreate("grpckt")
            }
            it.builtins { maybeCreate("kotlin") }
        }
    }
}

// Lint only our own sources, never the generated protobuf/gRPC code.
afterEvaluate {
    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
        setSource(fileTree("src") { include("**/*.kt", "**/*.kts") })
    }
}
