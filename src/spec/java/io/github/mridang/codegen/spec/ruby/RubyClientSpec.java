package io.github.mridang.codegen.spec.ruby;

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
public class RubyClientSpec extends AbstractIntegrationSpec {

  private static final String MODULE_NAME = "OpigenClient";
  private static final String GEM_NAME = "opigen_client";
  private static final Path TEST_PROJECT_PATH = Paths.get("src/spec/resources/testprojects/rspec");

  @Override
  protected String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.2-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apt-get update && apt-get install -y libcurl4 build-essential --no-install-recommends 2>/dev/null",
      "bundle install --quiet",
      "API_BASE_URL=http://prism:4010 bundle exec rspec --format documentation"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    // Not used - we run rspec directly
    return "";
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException("Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }

    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
    logger.info("Copied Ruby test project from {} to {}", TEST_PROJECT_PATH, tempOutputDir);
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
  void shouldGenerateRubyClient() {
    // Generate client - generator creates its own lib/ folder
    generateClientToDirectory(
        Map.of(
            CodegenConstants.GEM_NAME, GEM_NAME,
            CodegenConstants.MODULE_NAME, MODULE_NAME),
        tempOutputDir);

    assertThat(tempOutputDir.resolve("lib")).exists();
  }

  @Test
  @Order(2)
  void shouldRunRubyTests() {
    String prismUrl = startPrismServer();

    // Generate client - generator creates its own lib/ folder
    generateClientToDirectory(
        Map.of(
            CodegenConstants.GEM_NAME, GEM_NAME,
            CodegenConstants.MODULE_NAME, MODULE_NAME),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("RSpec tests failed:\n%s", result.output())
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
