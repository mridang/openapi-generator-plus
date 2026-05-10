plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.example.petstore"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("src/main/kotlin")
            dependencies {
                implementation("io.ktor:ktor-client-core:3.1.3")
                implementation("io.ktor:ktor-client-encoding:3.1.3")
                implementation("io.ktor:ktor-http:3.1.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        val jvmMain by getting {
            kotlin.srcDirs("src/jvmMain/kotlin")
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.1.3")
                implementation("io.opentelemetry:opentelemetry-api:1.43.0")
            }
        }
        val jvmTest by getting {
            kotlin.srcDirs("src/test/kotlin")
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("io.ktor:ktor-client-mock:3.1.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
                implementation("org.testcontainers:testcontainers:1.21.4")
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
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

tasks.register("test") {
    dependsOn("jvmTest")
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
