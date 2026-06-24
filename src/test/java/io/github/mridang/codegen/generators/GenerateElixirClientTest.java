package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateElixirClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/elixir").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient(
        "elixir-plus",
        Map.of("packageName", "petstore_client", "moduleName", "PetstoreClient"),
        OUTPUT_DIR);
  }

  /**
   * H8 regression: generated typespecs must never reference an undefined type.
   *
   * <p>(a) An options-bearing operation must {@code @spec} the concrete
   * per-operation options module type (e.g. {@code AddPetOptions.t()}), never a
   * literal {@code Options.t()} for which no module exists. (b) Model fields and
   * operation arg/return types must be fully-qualified struct types
   * ({@code PetstoreClient.Models.Pet.t()}), never bare aliases ({@code Pet},
   * {@code [Tag]}, {@code Order}) which Elixir flags as unknown types. (c) Enum
   * modules must define a {@code @type t} atom-union so references to them
   * resolve.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). It deliberately does NOT regenerate into a JUnit
   * {@code @TempDir}: the Elixir post-process runs the formatter in Docker, which
   * leaves root-owned {@code _build/.mix} artifacts that JUnit's temp-dir cleanup
   * cannot delete on the CI container. A READ lock on the shared resource orders
   * it after {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void typespecsReferenceOnlyDefinedTypes() throws IOException {
    final Path libDir = OUTPUT_DIR.resolve("lib/petstore_client");

    // (a) Per-operation options module type, not a literal Options.t().
    final String petApi = Files.readString(libDir.resolve("api/pet_api.ex"));
    assertThat(petApi)
        .as("options @spec must use the real per-operation options module type")
        .contains("PetstoreClient.Api.Options.AddPetOptions.t()")
        // The unqualified literal `Options.t()` (rendered as ", Options.t(),"
        // before this fix) names no module; every options arg must be qualified.
        .doesNotContain(", Options.t()");

    // (b) Operation arg/return types are fully-qualified struct typespecs,
    // including the inner element type of a list return ([Photo] -> [...t()]).
    // The positive assertions prove the @spec return types are fully qualified.
    // We deliberately do NOT assert doesNotContain("{:ok, Pet}") against the whole
    // file: the human-readable function doc comment legitimately uses the short
    // `{:ok, Pet}` form in prose (e.g. "* `{:ok, Pet}` on success."), which is not
    // a typespec. Asserting the qualified form is present in @spec position is the
    // precise guard against the bare-alias-return bug.
    assertThat(petApi)
        .as("model body/return types must be qualified Module.t() typespecs")
        .contains("{:ok, PetstoreClient.Models.Pet.t()}")
        .contains("{:ok, [PetstoreClient.Models.Photo.t()]}");

    // (b) Model field types are fully-qualified, including inner [...] types.
    final String pet = Files.readString(libDir.resolve("models/pet.ex"));
    assertThat(extractTypeBlock(pet))
        .as("model field typespecs must be qualified Module.t() (incl. list inner)")
        .contains("category: PetstoreClient.Models.Category.t()")
        .contains("tags: [PetstoreClient.Models.Tag.t()]")
        // A bare alias in typespec position is the bug we are guarding against.
        .doesNotContain("category: Category")
        .doesNotContain("[Tag]");

    // (c) Enum modules define a @type t atom-union (Availability is a string enum).
    final String availability = Files.readString(libDir.resolve("models/availability.ex"));
    assertThat(availability)
        .as("enum modules must declare @type t so references to them resolve")
        .contains("@type t ::")
        .contains(":available")
        .contains(":sold")
        .contains(":on_hold");
  }

  /** Returns the {@code %__MODULE__{...}} typespec block so field assertions ignore prose. */
  private static String extractTypeBlock(String source) {
    final int start = source.indexOf("@type t :: %__MODULE__{");
    assertThat(start).as("model must declare a struct @type").isGreaterThanOrEqualTo(0);
    final int end = source.indexOf("}", start);
    return source.substring(start, end);
  }
}
