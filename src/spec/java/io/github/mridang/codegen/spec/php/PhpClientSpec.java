package io.github.mridang.codegen.spec.php;

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
public class PhpClientSpec extends AbstractIntegrationSpec {

  private static final String INVOKER_PACKAGE = "PetstoreClient";
  private static final Path TEST_PROJECT_PATH = Paths.get("src/spec/resources/testprojects/phptest");

  @Override
  protected String getGeneratorName() {
    return "php-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("composer:2");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "composer install --no-interaction --prefer-dist",
      "API_BASE_URL=http://prism:4010 vendor/bin/phpunit --testdox"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    // Not used - we run phpunit directly
    return "";
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException("Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }

    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
    logger.info("Copied PHP test project from {} to {}", TEST_PROJECT_PATH, tempOutputDir);
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
  void shouldGeneratePhpClient() {
    // Generate client - generator creates its own lib/PetstoreClient/ structure
    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, INVOKER_PACKAGE),
        tempOutputDir);

    assertThat(tempOutputDir.resolve("lib/Api")).exists();
    assertThat(tempOutputDir.resolve("lib/Model")).exists();
  }

  @Test
  @Order(2)
  void shouldRunPhpTests() {
    String prismUrl = startPrismServer();

    // Generate client - generator creates its own lib/PetstoreClient/ structure
    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, INVOKER_PACKAGE),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("PHPUnit tests failed:\n%s", result.output())
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
