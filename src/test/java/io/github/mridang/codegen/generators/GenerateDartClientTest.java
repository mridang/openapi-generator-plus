package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateDartClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/dart").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-dart", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient("dart-plus", Map.of("packageName", "petstore_client"), OUTPUT_DIR);
  }

  /**
   * L3 regression: a parameter declared {@code deprecated: true} in the spec must
   * carry the language deprecation marker on the per-operation Options field, not
   * just on operations and model properties.
   *
   * <p>The fixture op {@code findPetsByStatus} has a query parameter {@code status}
   * declared {@code deprecated: true}. The generated {@code FindPetsByStatusOptions}
   * must annotate the {@code status} field with Dart's {@code @Deprecated(...)},
   * mirroring how {@code @Deprecated} is already emitted on deprecated operations
   * and model properties. The field type stays {@code String?} ({@code status} has
   * {@code allowEmptyValue: true}); only the annotation is added.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). A READ lock on the shared resource orders it after
   * {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-dart", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final Path optionsFile =
        OUTPUT_DIR.resolve("lib/src/api/options/find_pets_by_status_options.dart");
    final String source = Files.readString(optionsFile);

    assertThat(source)
        .as("deprecated query param `status` must carry @Deprecated on the options field")
        .contains("@Deprecated('This parameter is deprecated.')")
        // The annotation must sit on the status field, and the type stays String?
        // (allowEmptyValue:true) — only the deprecation marker is added.
        .containsPattern("@Deprecated\\('This parameter is deprecated\\.'\\)\\s*\\n\\s*final String\\? status;");
  }
}
