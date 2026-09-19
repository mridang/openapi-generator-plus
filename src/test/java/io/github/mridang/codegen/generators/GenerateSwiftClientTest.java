package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateSwiftClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/swift").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-swift", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient("swift-plus", Map.of("packageName", "PetstoreClient"), OUTPUT_DIR);
  }

  /**
   * A query/header/form/cookie parameter declared {@code deprecated: true} in the
   * spec must surface that signal on the per-operation Options struct field, not
   * just on operations and model properties. The fixture op {@code
   * findPetsByStatus} has a {@code status} query parameter marked deprecated, so
   * its generated {@code FindPetsByStatusOptions.status} field must carry the
   * Swift {@code @available(*, deprecated, ...)} annotation, mirroring the
   * operation (api.mustache) and model-property (model.mustache) deprecation
   * idioms.
   *
   * <p>The type is intentionally left untouched ({@code String?}): {@code status}
   * has {@code allowEmptyValue: true} and must still accept an empty value — only
   * the deprecation marker is added.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). A READ lock on the shared resource orders it after
   * {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-swift", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final String options =
        Files.readString(
            OUTPUT_DIR.resolve(
                "Sources/PetstoreClient/Api/Options/FindPetsByStatusOptions.swift"));

    // The status field must carry the Swift deprecation availability annotation.
    assertThat(options)
        .as("deprecated query param `status` must be marked deprecated in the Options struct")
        .contains("@available(*, deprecated, message: \"This parameter is deprecated.\")")
        .contains("public let status: String?");
  }
}
