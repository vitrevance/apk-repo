plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.apkrepo"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // APK analysis (pure-JVM, no Android SDK needed in the API image):
    // AOSP apksig (build-tools 30.0.2 republication on Maven Central) for
    // signature verification; ARSCLib for binary AndroidManifest/resources parsing.
    implementation("org.signserver.apksig:apksig:30.0.2-signserver5-1")
    implementation("io.github.reandroid:ARSCLib:1.3.5")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // Self-signed certificate generation for APK test fixtures.
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.87")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.87")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Docker Engine >= 29 rejects API versions older than 1.44, but Testcontainers 1.x
    // pins its docker-java client to API 1.32 when no explicit version is configured.
    // Explicitly select a supported API version so Testcontainers can talk to the engine.
    systemProperty("api.version", "1.44")
}
