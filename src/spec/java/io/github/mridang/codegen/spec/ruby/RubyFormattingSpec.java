package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Ruby code is already properly formatted according to rubocop layout rules.
 * If this test fails, the Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyFormattingSpec extends AbstractFormattingSpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "bundle install --quiet",
      "bundle exec rubocop --only Layout --format simple"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".rb";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient");
  }

  @Override
  @Nullable
  protected String getInlineCommentPattern() {
    return null;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
