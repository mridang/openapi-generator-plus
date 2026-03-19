package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.testcontainers.utility.DockerImageName;

interface JavaSpec extends LanguageSpec, DockerImageSpec {

  default String getGeneratorName() {
    return "java-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("maven:3.9-eclipse-temurin-21");
  }

  default String getDockerImage() {
    return "eclipse-temurin:17-jdk-jammy";
  }

  default Map<String, Object> getCodegenProperties() {
    return Map.of(
        "modelPackage", "com.example.petstore.models",
        "apiPackage", "com.example.petstore.api",
        "invokerPackage", "com.example.petstore");
  }
}
