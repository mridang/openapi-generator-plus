package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Java code is already properly formatted according to
 * google-java-format. If this test fails, the Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaFormattingSpec extends AbstractFormattingSpec {

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
      "curl -sL -o /tmp/gjf.jar https://github.com/google/google-java-format/releases/download/v1.25.2/google-java-format-1.25.2-all-deps.jar",
      "find /app -name '*.java' | xargs java -jar /tmp/gjf.jar --dry-run --set-exit-if-changed"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".java";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
        CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
        CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME);
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
