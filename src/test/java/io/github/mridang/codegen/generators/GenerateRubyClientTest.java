package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateRubyClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/ruby").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-ruby", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient(
        "ruby-plus",
        Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"),
        OUTPUT_DIR);
  }

  /**
   * A query/header/form/cookie parameter declared {@code deprecated: true} in the
   * spec must surface that signal on the per-operation Options object, not just on
   * operations and model properties. The fixture op {@code findPetsByStatus} has a
   * {@code status} query parameter marked deprecated, so its generated {@code
   * FindPetsByStatusOptions} {@code status} accessor must carry the YARD {@code
   * # @deprecated} tag, mirroring the operation (api.mustache) and model-property
   * (model.mustache) deprecation idioms.
   *
   * <p>The type is intentionally left untouched ({@code String}): {@code status}
   * has {@code allowEmptyValue: true} and must still accept an empty value — only
   * the deprecation marker is added.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). A READ lock on the shared resource orders it after
   * {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-ruby", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final String options =
        Files.readString(
            OUTPUT_DIR.resolve(
                "lib/petstore_client/api/options/find_pets_by_status_options.rb"));

    // The status accessor must carry the YARD deprecation tag.
    assertThat(options)
        .as("deprecated query param `status` must be marked deprecated in the Options object")
        .contains("# @deprecated This parameter is deprecated.")
        .contains("attr_reader :status");
  }
}
