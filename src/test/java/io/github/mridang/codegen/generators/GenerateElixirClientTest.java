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

class GenerateElixirClientTest {

  private static final Path OUTPUT_DIR =
      Path.of("src/spec/resources/generated/elixir").toAbsolutePath();

  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    ClientGenerator.generateClient(
        "elixir-plus",
        Map.of(
            "packageName", "petstore_client",
            "moduleName", "PetstoreClient",
            "repositoryUrl", "https://example.com/acme/petstore-client",
            "homepageUrl", "https://example.com/petstore",
            "documentationUrl", "https://example.com/petstore/docs",
            "issueTrackerUrl", "https://example.com/acme/petstore-client/issues",
            "authors", "Ada Lovelace, Grace Hopper"),
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

  /**
   * Regression: a query/header/form/cookie parameter declared {@code deprecated:
   * true} in the spec must surface its deprecation on the per-operation Options
   * field, mirroring how operations and model schemas already render
   * {@code @deprecated}. The fixture op {@code findPetsByStatus} has a deprecated
   * {@code status} query parameter; its generated {@code FindPetsByStatusOptions}
   * struct must carry an {@code @deprecated} marker on the {@code status} field.
   * The type stays {@code String.t() | nil} (the param has
   * {@code allowEmptyValue: true} and must still accept an empty value) — only the
   * deprecation marker is added.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void deprecatedParamStatusIsMarkedDeprecatedInOptions() throws IOException {
    final Path optionsFile =
        OUTPUT_DIR.resolve(
            "lib/petstore_client/api/options/find_pets_by_status_options.ex");
    final String options = Files.readString(optionsFile);

    assertThat(options)
        .as("deprecated `status` param must carry an @deprecated marker in its Options field")
        .contains("# @deprecated This parameter is deprecated.")
        // The deprecation must annotate the `status` field, and the type must be
        // unchanged (allowEmptyValue:true => still a plain String.t() | nil).
        .contains("status: String.t() | nil");
  }

  /**
   * F894: the repository, homepage, documentation and issue-tracker URLs and the authors reach
   * the Hex manifest, the docs URLs and the README.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void manifestOptionsReachTheMixProjectAndTheReadme() throws IOException {
    final String mix = Files.readString(OUTPUT_DIR.resolve("mix.exs"));
    assertThat(mix)
        .contains("source_url: \"https://example.com/acme/petstore-client\"")
        .contains("homepage_url: \"https://example.com/petstore\"")
        .contains("maintainers: [\"Ada Lovelace\", \"Grace Hopper\"]")
        .contains("\"Repository\" => \"https://example.com/acme/petstore-client\"")
        .contains("\"Homepage\" => \"https://example.com/petstore\"")
        .contains("\"Documentation\" => \"https://example.com/petstore/docs\"")
        .contains("\"Issues\" => \"https://example.com/acme/petstore-client/issues\"");

    final String readme = Files.readString(OUTPUT_DIR.resolve("README.md"));
    assertThat(readme)
        .contains("- Authors: Ada Lovelace, Grace Hopper")
        .contains("- Repository: <https://example.com/acme/petstore-client>")
        .contains("- Homepage: <https://example.com/petstore>")
        .contains("- Documentation: <https://example.com/petstore/docs>")
        .contains("- Issue tracker: <https://example.com/acme/petstore-client/issues>");
  }

  /**
   * S8 (F1154): the server of an operation that declares one travels in the trailing {@code
   * opts} keyword list next to the per-call settings, after the options struct, so adding a
   * server never moves a positional argument.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void theServerTravelsInTheTrailingOptsAfterTheOptionsStruct() throws IOException {
    final String petApi = Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/api/pet_api.ex"));
    assertThat(petApi)
        .contains("Keyword.validate!(opts, [:timeout, :headers, :max_retries, :server])")
        .doesNotContain(", server \\\\ nil")
        .doesNotContain("%__MODULE__{} = api, server");
  }

  /**
   * F827: every per-operation options struct carries the per-call timeout, headers and
   * max_retries next to its parameters.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void everyOptionsStructCarriesThePerCallSettings() throws IOException {
    final Path dir = OUTPUT_DIR.resolve("lib/petstore_client/api/options");
    try (Stream<Path> files = Files.list(dir)) {
      final java.util.List<Path> all =
          files.filter(p -> p.toString().endsWith(".ex")).collect(java.util.stream.Collectors.toList());
      assertThat(all).isNotEmpty();
      for (final Path file : all) {
        assertThat(Files.readString(file))
            .as(file.getFileName().toString())
            .contains("timeout: pos_integer() | nil")
            .contains("headers: %{optional(String.t()) => String.t()} | nil")
            .contains("max_retries: non_neg_integer() | nil")
            .doesNotContainPattern("(?m)^\\s*auth:");
      }
    }
  }

  /**
   * F857: the client takes one optional credential per security scheme, named after the scheme,
   * and an operation resolves the credential of the first scheme its own security lists that
   * the client holds; an operation declared {@code security: []} sends none.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void anOperationUsesTheCredentialOfTheFirstSchemeItListsThatTheClientHolds() throws IOException {
    final String client =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/client.ex"));
    assertThat(client)
        .contains("{:pet_store_basic, \"petStoreBasic\"}")
        .contains("{:machine_auth, \"machineAuth\"}")
        .contains("def new(authenticator, transport_options \\\\ nil, schemes \\\\ [], opts \\\\ [])")
        .contains("Keyword.validate!(schemes, Keyword.keys(@schemes))");
    final String petApi =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/api/pet_api.ex"));
    assertThat(petApi)
        .contains(
            "PetstoreClient.Api.BaseApi.scheme_auth(api, [\"adminBasic\", \"machineAuth\"])")
        .contains(
            "PetstoreClient.Api.BaseApi.scheme_auth(api, [\"apiKeyHeader\", \"petStoreBearer\"])");
    final String storeApi =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/api/store_api.ex"));
    assertThat(storeApi).contains("auth = PetstoreClient.Api.BaseApi.no_auth()");
  }

  /**
   * F827: an operation with no query, header, form or cookie parameter and no security still
   * takes an options struct, which has the per-call settings and no auth field.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void anOperationWithNoParametersAndNoSecurityStillTakesOptions() throws IOException {
    final String options =
        Files.readString(
            OUTPUT_DIR.resolve("lib/petstore_client/api/options/get_inventory_options.ex"));
    assertThat(options)
        .contains("max_retries: non_neg_integer() | nil")
        .doesNotContain("auth:");
    final String storeApi =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/api/store_api.ex"));
    assertThat(storeApi)
        .contains("def get_inventory(%__MODULE__{} = api, options \\\\ nil, opts \\\\ [])")
        .contains("PetstoreClient.Api.BaseApi.call_settings(options, opts)");
  }

  /**
   * F862: each scheme's authenticator reads the variable named for that scheme and field, and
   * the standalone authenticators keep the generic prefix.
   */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void eachSchemeAuthenticatorReadsItsOwnEnvironmentVariable() throws IOException {
    final Path auth = OUTPUT_DIR.resolve("lib/petstore_client/auth");
    assertThat(Files.readString(auth.resolve("service_token_authenticator.ex")))
        .contains("def new(host, token \\\\ nil)")
        .contains("\"SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN\"");
    assertThat(Files.readString(auth.resolve("oauth/legacy_auth_password_authenticator.ex")))
        .contains("\"SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME\"")
        .contains("\"SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET\"");
    assertThat(Files.readString(auth.resolve("base_authenticator.ex")))
        .contains("\"SWAGGER_PETSTORE_OPEN_API_3_0_\" <> field");
  }

  /** F878: a union module has a typed constructor, predicate and accessor per model variant. */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void aUnionModuleHasTypedFunctionsForEveryModelVariant() throws IOException {
    final String union =
        Files.readString(
            OUTPUT_DIR.resolve("lib/petstore_client/models/reserve_stock_response.ex"));
    assertThat(union)
        .contains("def from_reservation(%PetstoreClient.Models.Reservation{} = value)")
        .contains("def reservation?(reserve_stock_response)")
        .contains("def as_reservation(reserve_stock_response)")
        .contains("def from_reservation_receipt(")
        .contains("def reservation_receipt?(reserve_stock_response)")
        .contains("def as_reservation_receipt(reserve_stock_response)");
  }

  /** F866: a property whose $ref carries a sibling default starts at it, after the plain atoms. */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void aRefPropertyWithASiblingDefaultStartsAtIt() throws IOException {
    final String model =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/models/stock_item.ex"));
    assertThat(model).contains("availability: :on_hold");
    assertThat(model.indexOf(":matrix")).isLessThan(model.indexOf("availability: :on_hold"));
  }

  /** F1062: an operation with several 2xx schemas passes the status table to the decoder. */
  @Test
  @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
  void severalSuccessResponsesDecodeByStatus() throws IOException {
    final String storeApi =
        Files.readString(OUTPUT_DIR.resolve("lib/petstore_client/api/store_api.ex"));
    assertThat(storeApi)
        .contains(
            "{\"ReserveStockResponse\", [{\"200\", \"Reservation\"}, {\"201\","
                + " \"ReservationReceipt\"}]}");
  }

  /** Returns the {@code %__MODULE__{...}} typespec block so field assertions ignore prose. */
  private static String extractTypeBlock(String source) {
    final int start = source.indexOf("@type t :: %__MODULE__{");
    assertThat(start).as("model must declare a struct @type").isGreaterThanOrEqualTo(0);
    final int end = source.indexOf("}", start);
    return source.substring(start, end);
  }

  /**
   * The probe spec's only security scheme is a bearer token. The OAuth2 errors
   * belong to the root error hierarchy, so they must be generated anyway; the
   * authenticator tests, by contrast, must follow the schemes the spec declares,
   * which only works when they are registered after the spec has been read.
   */
  @Test
  void oauth2ErrorsAndSchemeTestsFollowTheSpec() throws IOException {
    final Path out = Path.of("target", "probe-schemes", "elixir-plus").toAbsolutePath();
    if (Files.exists(out)) {
      try (Stream<Path> walk = Files.walk(out)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
    ProbeGenerator.generate(
        "elixir-plus",
        Map.of("packageName", "probe_client", "moduleName", "ProbeClient", "generateTests", "true", "skipFormatter", "true"),
        out);

    assertThat(out.resolve("lib/probe_client/errors/oauth2_server_error.ex")).exists();
    assertThat(out.resolve("lib/probe_client/errors/oauth2_token_error.ex")).exists();
    assertThat(out.resolve("test/bearer_authenticator_test.exs")).exists();
    assertThat(out.resolve("test/basic_authenticator_test.exs")).doesNotExist();
    assertThat(out.resolve("test/api_key_authenticator_test.exs")).doesNotExist();
    assertThat(out.resolve("test/oauth2_token_manager_test.exs")).doesNotExist();
    assertThat(out.resolve("test/openid_connect_authenticator_test.exs")).doesNotExist();
  }
}
