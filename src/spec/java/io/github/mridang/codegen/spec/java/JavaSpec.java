package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("java")
interface JavaSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "java-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("maven:3.9-eclipse-temurin-21");
  }

  @Override
  default String getDockerImage() {
    return "eclipse-temurin:17-jdk-jammy";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of(
        "modelPackage", "com.example.petstore.models",
        "apiPackage", "com.example.petstore.api",
        "invokerPackage", "com.example.petstore");
  }
}
