plugins {
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"

    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
}

group = "org.workflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // API / Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security (RBAC)
    implementation("org.springframework.boot:spring-boot-starter-security")


    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

    // Database & ORM
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin Core
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

kotlin {
    jvmToolchain(25)
}

springBoot {
    mainClass.set("org.workflow.WorkflowMainApplicationKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val dockerComposeFile = "src/docker-compose.yml"
val dockerCmd = "/usr/local/bin/docker"
tasks.register<Exec>("dbUp") {
    group = "workflow"
    description = "Start PostgreSQL container for local development"
    commandLine(dockerCmd, "compose", "-f", dockerComposeFile, "up", "-d")
}

tasks.register<Exec>("dbDown") {
    group = "workflow"
    description = "Stop PostgreSQL container"
    commandLine(dockerCmd, "compose", "-f", dockerComposeFile, "down")
}

tasks.register<Exec>("dbReset") {
    group = "workflow"
    description = "Stop and remove PostgreSQL container + volume (destructive)"
    commandLine(dockerCmd, "compose", "-f", dockerComposeFile, "down", "-v")
}

tasks.register<Exec>("dbLogs") {
    group = "workflow"
    description = "Show PostgreSQL container logs"
    commandLine(dockerCmd, "compose", "-f", dockerComposeFile, "logs", "postgres")
}

/**
 * Polls `docker inspect` until the postgres container reports status = "healthy".
 * Prevents bootRun from starting before PostgreSQL accepts connections.
 * Uses ProcessBuilder (not exec{}) because doLast runs in Task scope, not Project scope.
 */
tasks.register("waitForDb") {
    group = "workflow"
    description = "Block until the PostgreSQL container is healthy"
    dependsOn("dbUp")

    doLast {
        val maxWaitSeconds = 30
        val pollIntervalMs = 2000L
        var waited = 0

        println("Waiting for PostgreSQL to become healthy...")
        while (waited < maxWaitSeconds) {
            val process = ProcessBuilder(
                "docker", "inspect",
                "--format", "{{.State.Health.Status}}",
                "workflow-postgres"
            ).redirectErrorStream(true).start()
            val status = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (status == "healthy") {
                println("PostgreSQL is healthy after ${waited}s.")
                return@doLast
            }
            println("  status='$status', retrying in ${pollIntervalMs / 1000}s...")
            Thread.sleep(pollIntervalMs)
            waited += (pollIntervalMs / 1000).toInt()
        }
        throw GradleException(
            "PostgreSQL did not become healthy within ${maxWaitSeconds}s. Run ./gradlew dbLogs for details."
        )
    }
}

tasks.named("bootRun") {
    mustRunAfter("waitForDb")
}

tasks.register("dev") {
    group = "workflow"
    description = "Start DB, wait until healthy, then run Spring Boot"
    dependsOn("waitForDb", "bootRun")
}
