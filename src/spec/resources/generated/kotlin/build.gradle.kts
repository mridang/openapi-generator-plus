// SPDX-License-Identifier: MIT
plugins {
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("org.jetbrains.dokka") version "1.9.20"
    `maven-publish`
}

group = "com.example.petstore"
version = "1.0.0"

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}

repositories {
    mavenCentral()
}

kotlin {
    // Use whatever JDK is on PATH (Docker container provides a recent JDK).
    // Toolchain auto-provisioning requires extra repos configured; we skip that.
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

// Static analysis & formatting: detekt provides lint + static analysis,
// ktlint enforces Kotlin coding conventions. Detekt also pulls in the
// `detekt-formatting` and `detekt-rules-libraries` rulesets for extra
// coverage (formatting parity with ktlint + library-author rules).
detekt {
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    source.setFrom(files("src/main/kotlin", "src/jvmMain/kotlin"))
    config.setFrom(files("detekt.yml"))
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.7")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

ktlint {
    version.set("1.5.0")
    android.set(false)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // Bumped from 512m: the integration suite spawns one ktor HttpClient
    // per test (24+ in PetApiTest alone) and 512m caused GC pauses long
    // enough that ktor request timeouts surfaced as ConnectException.
    maxHeapSize = "1024m"
    // Run test classes in parallel JVM forks. Each fork is its own JVM
    // so testcontainers (WireMock, Squid, Prism) and Ktor clients live
    // in isolation per fork — no shared-state races possible. Cap at
    // half the CPU count to leave headroom for the Docker daemon and
    // the host build.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
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
