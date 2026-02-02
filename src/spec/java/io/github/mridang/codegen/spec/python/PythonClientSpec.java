package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PythonClientSpec extends AbstractIntegrationSpec {

  private static final String PACKAGE_NAME = "petstore_client";
  private static final Path TEST_PROJECT_PATH = Paths.get("src/spec/resources/testprojects/pytest");

  @Override
  protected String getGeneratorName() {
    return "python-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("python:3.11-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "pip install --quiet -r requirements.txt",
      "API_BASE_URL=http://prism:4010 python -m pytest tests/ -v"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    // Not used - we run pytest directly
    return "";
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException("Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }

    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
    logger.info("Copied Python test project from {} to {}", TEST_PROJECT_PATH, tempOutputDir);
  }

  private void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> stream = Files.walk(source)) {
      stream.forEach(
          sourcePath -> {
            try {
              Path targetPath = target.resolve(source.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath);
              } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (IOException e) {
              throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
          });
    }
  }

  @Test
  @Order(1)
  void shouldGeneratePythonClient() {
    // Generate client into the temp directory (creates petstore_client/ package)
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    assertThat(tempOutputDir.resolve(PACKAGE_NAME)).exists();
    assertThat(tempOutputDir.resolve(PACKAGE_NAME + "/api")).exists();
    assertThat(tempOutputDir.resolve(PACKAGE_NAME + "/models")).exists();
  }

  @Test
  @Order(2)
  void shouldRunPythonTests() {
    String prismUrl = startPrismServer();

    // Generate client into the temp directory
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("pytest tests failed:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(Map<String, Object> additionalProperties, Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    logger.info("Generating {} client from spec: {}", getGeneratorName(), specPath);
    logger.info("Output directory: {}", outputDir);

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator = new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();

    logger.info("Code generation complete.");
  }
}
