import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    jacoco
}

group = "com.drift"
version = "0.0.1-SNAPSHOT"
description = "room-service"

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
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

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
    implementation("io.grpc:grpc-kotlin-stub:1.4.3")
    implementation("com.google.protobuf:protobuf-kotlin:4.36.1")

    // ── Scheduling (ShedLock) ───────────────────────────────────────────────
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")

    // ── Testing (unit + integration tests together in src/test/kotlin) ──────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("com/drift/room/**/config/**", "com/drift/room/**/persistence/**", "com/drift/room/RoomServiceApplication*")
                }
            },
        )
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData(tasks.test.get())
    sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.files.map {
                fileTree(it) {
                    exclude("com/drift/room/**/config/**", "com/drift/room/**/persistence/**", "com/drift/room/RoomServiceApplication*")
                }
            },
        )
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
