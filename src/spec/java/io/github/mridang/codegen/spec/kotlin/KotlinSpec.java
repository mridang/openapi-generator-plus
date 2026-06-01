package io.github.mridang.codegen.spec.kotlin;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("kotlin")
interface KotlinSpec extends LanguageSpec, DockerImageSpec {

    @Override
    default String getGeneratorName() {
        return "kotlin-plus";
    }

    @Override
    default DockerImageName getRuntimeImage() {
        return DockerImageName.parse("gradle:9-jdk21");
    }

    @Override
    default String getDockerImage() {
        return "gradle:9-jdk21";
    }

    @Override
    default Map<String, Object> getCodegenProperties() {
        return Map.of(
                "modelPackage", "com.example.petstore.models",
                "apiPackage", "com.example.petstore.api",
                "invokerPackage", "com.example.petstore");
    }

    @Override
    default Map<String, String> getCacheEnv() {
        /* GRADLE_USER_HOME owns the daemon, wrapper distros, resolved
         * dependency cache, and the per-task build-cache. Pair it with
         * org.gradle.caching=true via GRADLE_OPTS so the build-cache
         * actually fires — project build/ still gets wiped between specs
         * but Gradle repopulates compiled classes from the build-cache
         * subdir (inside GRADLE_USER_HOME, hence preserved across the
         * wipe) on cache hit instead of recompiling. */
        return Map.of(
                "GRADLE_USER_HOME", "/root/.cache/kotlin/gradle",
                "GRADLE_OPTS", "-Dorg.gradle.caching=true");
    }
}
