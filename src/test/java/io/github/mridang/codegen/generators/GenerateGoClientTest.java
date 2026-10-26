package io.github.mridang.codegen.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateGoClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/go").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-go", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient("go-plus", Map.of("packageName", "petstore"), OUTPUT_DIR);
  }

  /**
   * A query/header/form/cookie parameter declared {@code deprecated: true} in the
   * spec must surface that signal on the per-operation Options struct field, not
   * just on operations and model properties. The fixture op {@code
   * findPetsByStatus} has a {@code status} query parameter marked deprecated, so
   * its generated {@code FindPetsByStatusOptions.Status} field must carry the Go
   * deprecation doc comment ({@code // Deprecated:}), mirroring the operation
   * (api.mustache) and model-property (model.mustache) deprecation idioms.
   *
   * <p>The type is intentionally left untouched ({@code *string}): {@code status}
   * has {@code allowEmptyValue: true} and must still accept an empty value — only
   * the deprecation marker is added.
   *
   * <p>Asserts against the committed golden tree (the same output the {@code
   * generate} test rewrites). A READ lock on the shared resource orders it after
   * {@code generate}'s READ_WRITE regeneration.
   */
  @Test
  @ResourceLock(value = "generated-go", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final String options =
        Files.readString(
            OUTPUT_DIR.resolve("pkg/options/find_pets_by_status_options.go"));

    // The Status field must carry the Go deprecation doc comment.
    assertThat(options)
        .as("deprecated query param `status` must be marked deprecated in the Options struct")
        .contains("// Deprecated: This parameter is deprecated.")
        .contains("Status *string");
  }

  /**
   * The probe spec's only security scheme is a bearer token. The OAuth2 errors
   * belong to the root error hierarchy, so they must be generated anyway; the
   * authenticator tests, by contrast, must follow the schemes the spec declares,
   * which only works when they are registered after the spec has been read.
   */
  @Test
  void oauth2ErrorsAndSchemeTestsFollowTheSpec() throws IOException {
    final Path out = Path.of("target", "probe-schemes", "go-plus").toAbsolutePath();
    if (Files.exists(out)) {
      try (Stream<Path> walk = Files.walk(out)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
    ProbeGenerator.generate(
        "go-plus",
        Map.of("packageName", "probe", "generateTests", "true", "skipFormatter", "true"),
        out);

    assertThat(out.resolve("pkg/errors/oauth2_server_error.go")).exists();
    assertThat(out.resolve("pkg/errors/oauth2_token_error.go")).exists();
    assertThat(out.resolve("test/bearer_authenticator_test.go")).exists();
    assertThat(out.resolve("test/basic_authenticator_test.go")).doesNotExist();
    assertThat(out.resolve("test/api_key_authenticator_test.go")).doesNotExist();
    assertThat(out.resolve("test/oauth2_token_manager_test.go")).doesNotExist();
    assertThat(out.resolve("test/openid_connect_authenticator_test.go")).doesNotExist();
  }
}
