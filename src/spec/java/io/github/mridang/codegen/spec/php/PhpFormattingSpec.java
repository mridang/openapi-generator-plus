package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code is already properly formatted according to phpcs with PSR-12
 * standard. If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpFormattingSpec extends AbstractFormattingSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"vendor/bin/phpcs", "vendor/bin/php-cs-fixer check"};
  }

  @Override
  protected String getFileExtension() {
    return ".php";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir.resolve("lib");
  }

  @Override
  protected boolean includeFileForInlineCommentCheck(Path file) {
    return !file.getFileName().toString().equals("rector.php");
  }

  @Override
  protected boolean skipFileHeaderComments() {
    return true;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated PHP code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  /** F1071: a plural example is written directly under the parameter it belongs to. */
  @Test
  void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
    Path petApi = tempOutputDir.resolve("lib/Api/PetApi.php");
    String src = java.nio.file.Files.readString(petApi);
    assertThat(src)
        .as("php PetApi.php should list plural examples under their parameter")
        .contains(
            "     * @param int $petId ID of pet to return\n"
                + "     * @example 1 Small breed ID\n"
                + "     * @example 42 Large breed ID\n");
  }

  /** F893: the examples of an optional (options-object) parameter are kept in the API reference. */
  @Test
  void optionalParameterExamplesShouldBeKept() throws java.io.IOException {
    Path options = tempOutputDir.resolve("lib/Api/Options/FindPetsByStatusOptions.php");
    String src = java.nio.file.Files.readString(options);
    assertThat(src)
        .as("php FindPetsByStatusOptions.php should keep the examples of `status`")
        .contains("@example available Available only")
        .contains("@example sold Sold pets");
  }
}
