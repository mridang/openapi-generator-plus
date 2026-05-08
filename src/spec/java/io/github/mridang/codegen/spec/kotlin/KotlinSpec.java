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
}
