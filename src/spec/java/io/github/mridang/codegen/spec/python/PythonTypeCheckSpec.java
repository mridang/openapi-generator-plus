package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Python code passes mypy type checking.
 * If this test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PythonTypeCheckSpec extends AbstractIntegrationSpec {

  private static final String PACKAGE_NAME = "petstore_client";

  @Override
  protected String getGeneratorName() {
    return "python-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("python:3-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "pip install --quiet -r requirements.txt",
      "mypy " + PACKAGE_NAME + "/"
    };
  }

  @Test
  void generatedCodeShouldPassTypeChecking() {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Python code has type checking errors:\n%s", result.output())
        .isTrue();
  }
}
