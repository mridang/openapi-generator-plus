package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated PHP code is already properly formatted according to
 * phpcs with PSR-12 standard. If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpFormattingSpec extends AbstractFormattingSpec {

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
      "vendor/bin/phpcs --standard=PSR12 lib/"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".php";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient");
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated PHP code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
