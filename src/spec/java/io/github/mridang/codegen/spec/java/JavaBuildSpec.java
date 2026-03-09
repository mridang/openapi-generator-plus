package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code compiles cleanly under Error Prone and NullAway with zero
 * warnings. Uses failOnWarning and -Xlint:all with no package exclusions. If this test fails, the
 * Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaBuildSpec extends AbstractIntegrationSpec implements JavaSpec {

  private static final String PACKAGE_NAME = "com.example.petstore";
  private static final Path TEST_PROJECT_PATH =
      Paths.get("src/spec/resources/testprojects/javatest");

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn compile -B"};
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException(
          "Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }
    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
  }

  @Test
  void generatedCodeShouldCompileWithErrorProne() {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
            CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
            CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated Java code has Error Prone/NullAway violations:\n%s", result.output())
        .isTrue();
  }
}
