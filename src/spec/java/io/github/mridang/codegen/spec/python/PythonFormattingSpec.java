package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Python code is already properly formatted according to
 * ruff format. If this test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-python")
public class PythonFormattingSpec extends AbstractFormattingSpec implements PythonSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"ruff format --check ."};
  }

  @Override
  protected String getFileExtension() {
    return ".py";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir;
  }

  /**
   * The source root is the whole SDK, tests included, and a generated test asserts that an escaped
   * apostrophe (the decimal 39 reference) never reaches the wire. That literal is the test's data, so it is
   * allowed under {@code test/} only; the SDK package itself gets no allowance.
   */
  @Override
  protected boolean isAllowedNumericEntity(Path file, String entity) {
    return "&#39;".equals(entity) && tempOutputDir.relativize(file).startsWith("test");
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
        .withFailMessage("Generated Python code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  /** F1071: a plural example is written directly under the parameter it belongs to. */
  @Test
  void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
    Path petApi = tempOutputDir.resolve("petstore_client/api/pet_api.py");
    String src = java.nio.file.Files.readString(petApi);
    assertThat(src)
        .as("python pet_api.py should list plural examples under their parameter")
        .contains(
            ":param pet_id: ID of pet to return (required)\n"
                + "        # Small breed ID\n"
                + "        # A common small-breed pet identifier\n"
                + "        # >>> 1\n"
                + "        # Large breed ID\n"
                + "        # A common large-breed pet identifier\n"
                + "        # >>> 42\n");
  }

  /** F893: the examples of an optional (options-object) parameter are kept in the API reference. */
  @Test
  void optionalParameterExamplesShouldBeKept() throws java.io.IOException {
    Path options = tempOutputDir.resolve("petstore_client/api/options/find_pets_by_status_options.py");
    String src = java.nio.file.Files.readString(options);
    assertThat(src)
        .as("python find_pets_by_status_options.py should keep the examples of `status`")
        .contains("# Example for `status`: Available only\n")
        .contains("# Example for `status`: Sold pets\n");
  }
}
