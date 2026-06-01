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
    return DockerImageName.parse("maven:3.9-eclipse-temurin-25");
  }

  @Override
  default String getDockerImage() {
    return "eclipse-temurin:25-jdk";
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
    /* Maven reads its local repository path from -Dmaven.repo.local; routing
     * it under /root keeps resolved JARs around across JavaBuildSpec →
     * JavaClientSpec → JavaStaticAnalysisSpec → JavaFormattingSpec. Project
     * target/ stays local (relocating it would need a pom property), so this
     * caches deps only — which is most of the cold-start cost. */
    return Map.of("MAVEN_OPTS", "-Dmaven.repo.local=/root/.cache/java/m2");
  }
}
