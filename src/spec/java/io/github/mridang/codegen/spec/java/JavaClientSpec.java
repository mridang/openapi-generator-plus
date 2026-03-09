package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaClientSpec extends AbstractClientSpec {

  private static final String PACKAGE_NAME = "com.example.petstore";

  @Override
  protected String getGeneratorName() {
    return "java-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("maven:3.9-eclipse-temurin-21");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile test-compile -q -B",
      "API_BASE_URL=http://prism:4010 mvn test -q -B"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/javatest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
        CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
        CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME);
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/main/java/com/example/petstore/api")).exists();
    assertThat(outputDir.resolve("src/main/java/com/example/petstore/models")).exists();
  }
}
