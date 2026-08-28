import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
}

group = "uz.safecity"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.12.6"

dependencies {
    // --- Web / core ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Security / JWT ---
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // --- Persistence: JPA + PostGIS ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.hibernate.orm:hibernate-spatial:6.5.2.Final")
    runtimeOnly("org.postgresql:postgresql")

    // --- Redis (refresh tokens, cache) ---
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // --- RabbitMQ (async events: incidents, notifications) ---
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // --- WebSocket / STOMP (live map, notifications) ---
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // --- Kotlin ---
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- Object storage (MinIO / S3-compatible) ---
    implementation("io.minio:minio:8.5.11")

    // --- PDF generation (report export pipeline: HTML template -> PDF, see ReportGenerationService) ---
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

    // --- Swagger ---
    implementation("org.springdoc:springdoc-openapi-ui:1.8.0")
    implementation("org.springdoc:springdoc-openapi-kotlin:1.8.0")


    // --- Testing ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}