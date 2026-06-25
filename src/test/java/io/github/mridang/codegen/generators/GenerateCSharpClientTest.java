package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateCSharpClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/csharp").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-csharp", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient(
        "csharp-plus", Map.of("packageName", "PetstoreClient", "sourceFolder", "src"), OUTPUT_DIR);
  }

  /**
   * A query/header/form/cookie parameter declared {@code deprecated: true} in the
   * spec must surface that signal on the per-operation Options class field, not
   * just on operations and model properties. The fixture op {@code
   * findPetsByStatus} has a {@code status} query parameter marked deprecated, so
   * its generated {@code FindPetsByStatusOptions.Status} field must surface that
   * signal, mirroring the operation (api.mustache) and model-property
   * (model.mustache) deprecation idioms.
   *
   * <p>The deprecation is rendered as an XML-doc {@code <remarks>} note rather
   * than the enforced {@code [Obsolete]} attribute. {@code [Obsolete]} is
   * compile-enforced: the generated {@code PetApi} reads {@code options.Status}
   * internally to build the request, which trips {@code CS0618} under the strict
   * build's {@code TreatWarningsAsErrors}. The doc note still surfaces the
   * deprecation in IntelliSense while compiling cleanly, placing C# in the
   * doc-comment cohort (Go/Python/Ruby/Elixir) — the languages whose enforced
   * markers (Java/Kotlin/Swift/Dart/Rust) survive only because their builds do
   * not escalate self-consumption of a deprecated member to an error.
   *
   * <p>The type is intentionally left untouched ({@code string?}): {@code status}
   * has {@code allowEmptyValue: true} and must still accept an empty value — only
   * the deprecation marker is added.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). A READ lock on the shared resource orders it after
   * {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-csharp", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final String options =
        Files.readString(
            OUTPUT_DIR.resolve("src/PetstoreClient/Api/Options/FindPetsByStatusOptions.cs"));

    // The Status field must carry the C# doc-comment deprecation note and keep
    // its nullable string type (allowEmptyValue must still accept an empty value).
    assertThat(options)
        .as("deprecated query param `status` must be marked deprecated in the Options class")
        .contains("/// <remarks>This parameter is deprecated.</remarks>")
        .contains("public string? Status { get; init; }");
  }
}
