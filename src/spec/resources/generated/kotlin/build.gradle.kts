plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.example.petstore"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("io.opentelemetry:opentelemetry-api:1.43.0")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
    reports.junitXml.outputLocation.set(file(".out/reports"))
    /* Pass Docker env vars to the forked test JVM for DinD support */
    listOf(
        "DOCKER_HOST",
        "TESTCONTAINERS_HOST_OVERRIDE",
        "TC_HOST",
        "TESTCONTAINERS_RYUK_DISABLED",
        "HOST_APP_PATH"
    ).forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
}

kover {
    reports {
        total {
            xml {
                xmlFile.set(file(".out/coverage.xml"))
            }
        }
    }
}
