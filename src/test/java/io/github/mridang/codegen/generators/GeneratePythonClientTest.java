package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GeneratePythonClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/python").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient(
        "python-plus",
        Map.of("packageName", "petstore_client", "projectName", "petstore-client"),
        OUTPUT_DIR);
  }

  /**
   * L3 regression: a query/header/form/cookie parameter declared {@code
   * deprecated: true} in the spec must propagate its deprecation marker onto the
   * corresponding field of the per-operation Options dataclass.
   *
   * <p>The fixture operation {@code findPetsByStatus} declares its {@code status}
   * query parameter deprecated. The generated {@code FindPetsByStatusOptions}
   * dataclass must carry the Python deprecation note (the same {@code # ..
   * deprecated::} reST marker used for deprecated model properties) immediately
   * above the {@code status} field. The parameter type is unchanged — {@code
   * status} stays {@code Optional[StrictStr]} because it has {@code
   * allowEmptyValue: true} and must still accept an empty value.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites); a READ lock orders it after {@code generate}'s
   * READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final Path optionsFile =
        OUTPUT_DIR.resolve(
            "petstore_client/api/options/find_pets_by_status_options.py");
    final String source = Files.readString(optionsFile);

    final int statusFieldIdx = source.indexOf("status: Optional[StrictStr]");
    assertThat(statusFieldIdx)
        .as("FindPetsByStatusOptions must declare the status field")
        .isGreaterThanOrEqualTo(0);

    // The deprecation note must sit immediately above the status field, mirroring
    // the deprecated-model-property idiom, and the type must remain StrictStr
    // (allowEmptyValue: true => the param keeps its string type, no change).
    final String beforeStatus = source.substring(0, statusFieldIdx);
    assertThat(beforeStatus)
        .as("deprecated status param must carry a deprecation marker in Options")
        .endsWith("# .. deprecated:: This parameter is deprecated.\n    ");
  }
}
