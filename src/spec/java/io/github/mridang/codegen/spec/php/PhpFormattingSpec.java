package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code is already properly formatted according to phpcs with PSR-12
 * standard. If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpFormattingSpec extends AbstractFormattingSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
      "vendor/bin/phpcs"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".php";
  }

  @Override
  protected boolean includeFileForInlineCommentCheck(Path file) {
    return !file.getFileName().toString().equals("rector.php");
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
