package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated PHP code passes PHPStan static analysis at level 9.
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
      "composer install --no-interaction --prefer-dist",
      "vendor/bin/phpstan analyse lib/ --level=9 --no-progress"
    };
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
            "  level: 9",
            "  paths:",
            "    - lib",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has static analysis errors:\n%s", result.output())
        .isTrue();
  }
}
