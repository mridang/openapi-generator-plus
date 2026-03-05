package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Python code is already properly formatted according to
 * ruff format. If this test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PythonFormattingSpec extends AbstractIntegrationSpec {

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
      "pip install --quiet ruff",
      "ruff format --config 'line-length = 120' --config 'format.quote-style = \"single\"' --check " + PACKAGE_NAME + "/"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Python code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(Map<String, Object> additionalProperties, Path outputDir) {
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

    org.openapitools.codegen.DefaultGenerator generator = new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}
