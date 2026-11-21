package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Ruby code is already properly formatted according to rubocop layout rules.
 * If this test fails, the Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyFormattingSpec extends AbstractFormattingSpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"bundle exec rubocop --only Layout --format simple"};
  }

  @Override
  protected String getFileExtension() {
    return ".rb";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir.resolve("lib");
  }

  @Override
  @Nullable
  protected String getInlineCommentPattern() {
    return null;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  @Test
  void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
    Path petApi = tempOutputDir.resolve("lib/petstore/client/api/pet_api.rb");
    String src = java.nio.file.Files.readString(petApi);
    assertThat(src)
        .as("ruby pet_api.rb should attribute plural examples to their parameter")
        .contains("# Example for +pet_id+ — Small breed ID: 1")
        .contains("# Example for +pet_id+ — Large breed ID: 42");
  }

  @Test
  void pluralExamplesOfAnOptionalParameterShouldBeAttributedToParameter()
      throws java.io.IOException {
    Path options =
        tempOutputDir.resolve("lib/petstore/client/api/options/find_pets_by_status_options.rb");
    String src = java.nio.file.Files.readString(options);
    assertThat(src)
        .as("the options file should carry the named examples of its parameters")
        .contains("# Example for +status+ — Available only: available")
        .contains("# Example for +status+ — Sold pets: sold");
  }
}
