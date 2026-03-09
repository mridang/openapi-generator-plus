package io.github.mridang.codegen.spec.php;

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
 * Verifies that generated PHP code passes PHPStan static analysis at level 5.
 * If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpStaticAnalysisSpec extends AbstractIntegrationSpec {

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
      "cd /app"
          + " && composer require --quiet --no-interaction"
          + " guzzlehttp/guzzle:^7.3"
          + " guzzlehttp/psr7:^2.0"
          + " symfony/serializer:^7.0"
          + " symfony/property-access:^7.0"
          + " symfony/property-info:^7.0"
          + " phpdocumentor/reflection-docblock:^5.3"
          + " && composer require --dev --quiet --no-interaction phpstan/phpstan"
          + " && composer dump-autoload",
      "vendor/bin/phpstan analyse lib/ --level=5 --no-progress"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() throws IOException {
    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient"),
        tempOutputDir);

    // Write composer.json with PSR-4 autoloading so PHPStan can resolve project classes
    Files.writeString(
        tempOutputDir.resolve("composer.json"),
        String.join(
            "\n",
            "{",
            "  \"autoload\": {",
            "    \"psr-4\": {",
            "      \"PetstoreClient\\\\\": \"lib/\"",
            "    }",
            "  }",
            "}",
            ""));

    Files.writeString(
        tempOutputDir.resolve("phpstan.neon"),
        String.join(
            "\n",
            "parameters:",
            "  level: 5",
            "  paths:",
            "    - lib",
            "  treatPhpDocTypesAsCertain: false",
            "  ignoreErrors:",
            "    - identifier: classConstant.unused",
            "    - identifier: return.void",
            "    - identifier: binaryOp.invalid",
            "    - identifier: method.notFound",
            "    - identifier: notIdentical.alwaysTrue",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has static analysis errors:\n%s", result.output())
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
