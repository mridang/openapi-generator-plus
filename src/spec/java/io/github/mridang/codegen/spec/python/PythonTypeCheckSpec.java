package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
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
      "pip install --quiet mypy pydantic typing_extensions types-python-dateutil",
      "mypy " + PACKAGE_NAME + "/ --ignore-missing-imports"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldPassTypeChecking() throws IOException {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve("mypy.ini"),
        String.join(
            "\n",
            "[mypy]",
            "check_untyped_defs = True",
            "disallow_untyped_defs = False",
            "ignore_missing_imports = True",
            "",
            "[mypy-" + PACKAGE_NAME + ".object_serializer]",
            "ignore_errors = True",
            "",
            "[mypy-" + PACKAGE_NAME + "]",
            "ignore_errors = True",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Python code has type checking errors:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, java.nio.file.Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator =
        new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}
