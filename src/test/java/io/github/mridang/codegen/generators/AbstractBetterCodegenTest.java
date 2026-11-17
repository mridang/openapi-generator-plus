package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.csharp.BetterCSharpCodegen;
import io.github.mridang.codegen.generators.dart.BetterDartCodegen;
import io.github.mridang.codegen.generators.elixir.BetterElixirCodegen;
import io.github.mridang.codegen.generators.go.BetterGoCodegen;
import io.github.mridang.codegen.generators.java.BetterJavaCodegen;
import io.github.mridang.codegen.generators.kotlin.BetterKotlinCodegen;
import io.github.mridang.codegen.generators.node.BetterNodeCodegen;
import io.github.mridang.codegen.generators.php.BetterPHPCodegen;
import io.github.mridang.codegen.generators.python.BetterPythonCodegen;
import io.github.mridang.codegen.generators.ruby.BetterRubyCodegen;
import io.github.mridang.codegen.generators.rust.BetterRustCodegen;
import io.github.mridang.codegen.generators.swift.BetterSwiftCodegen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.CodegenServer;
import org.openapitools.codegen.CodegenServerVariable;

/**
 * Covers the identifier, enum literal and per-operation server naming that
 * {@link AbstractBetterCodegen} computes once for all twelve languages.
 */
class AbstractBetterCodegenTest {

    private static final Map<String, Supplier<AbstractBetterCodegen>> CODEGENS =
            Map.ofEntries(
                    Map.entry("java", BetterJavaCodegen::new),
                    Map.entry("kotlin", BetterKotlinCodegen::new),
                    Map.entry("csharp", BetterCSharpCodegen::new),
                    Map.entry("php", BetterPHPCodegen::new),
                    Map.entry("python", BetterPythonCodegen::new),
                    Map.entry("ruby", BetterRubyCodegen::new),
                    Map.entry("node", BetterNodeCodegen::new),
                    Map.entry("dart", BetterDartCodegen::new),
                    Map.entry("go", BetterGoCodegen::new),
                    Map.entry("rust", BetterRustCodegen::new),
                    Map.entry("swift", BetterSwiftCodegen::new),
                    Map.entry("elixir", BetterElixirCodegen::new));

    private static AbstractBetterCodegen codegen(String language) {
        return Objects.requireNonNull(CODEGENS.get(language)).get();
    }

    /* Each row: language, raw spec value, the exact source literal. A
       double-quoted language escapes " and leaves ' alone, a single-quoted one
       the reverse; ruby and php keep a raw newline; kotlin and dart escape $,
       elixir #{; csharp's template supplies the quotes itself. */
    private static List<List<String>> enumLiterals() {
        final List<List<String>> rows = new ArrayList<>();
        for (final String lang : List.of("java", "kotlin", "go", "rust", "swift", "elixir")) {
            rows.add(List.of(lang, "o'clock", "\"o'clock\""));
            rows.add(List.of(lang, "a\\b", "\"a\\\\b\""));
            rows.add(List.of(lang, "say \"hi\"", "\"say \\\"hi\\\"\""));
            rows.add(List.of(lang, "a\nb", "\"a\\nb\""));
        }
        for (final String lang : List.of("python", "ruby", "php", "dart", "node")) {
            rows.add(List.of(lang, "o'clock", "'o\\'clock'"));
            rows.add(List.of(lang, "a\\b", "'a\\\\b'"));
            rows.add(List.of(lang, "say \"hi\"", "'say \"hi\"'"));
        }
        for (final String lang : List.of("python", "dart", "node")) {
            rows.add(List.of(lang, "a\nb", "'a\\nb'"));
        }
        rows.add(List.of("ruby", "a\nb", "'a\nb'"));
        rows.add(List.of("php", "a\nb", "'a\nb'"));
        rows.add(List.of("csharp", "o'clock", "o'clock"));
        rows.add(List.of("csharp", "a\\b", "a\\\\b"));
        rows.add(List.of("csharp", "say \"hi\"", "say \\\"hi\\\""));
        rows.add(List.of("csharp", "a\\\"b", "a\\\\\\\"b"));
        rows.add(List.of("csharp", "a\nb", "a\\nb"));
        rows.add(List.of("kotlin", "$eq", "\"\\$eq\""));
        rows.add(List.of("dart", "$eq", "'\\$eq'"));
        rows.add(List.of("java", "$eq", "\"$eq\""));
        rows.add(List.of("elixir", "#{x}", "\"\\#{x}\""));
        rows.add(List.of("ruby", "#{x}", "'#{x}'"));
        rows.add(List.of("swift", "\\(x)", "\"\\\\(x)\""));
        return rows;
    }

    @Test
    @DisplayName("a string enum literal evaluates to exactly the spec value")
    void enumLiteralIsEscapedNotStripped() {
        final List<Executable> checks = new ArrayList<>();
        for (final List<String> row : enumLiterals()) {
            checks.add(() -> assertEquals(
                    row.get(2), codegen(row.get(0)).toEnumValue(row.get(1), "string"), row.toString()));
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("an enum value that sanitises to nothing or a number still names a legal constant")
    void enumConstantNamesAreLegalIdentifiers() {
        final List<Executable> checks = new ArrayList<>();
        for (final String language : CODEGENS.keySet()) {
            final AbstractBetterCodegen codegen = codegen(language);
            for (final String value : List.of(" ", "@@", "1st", "a-b")) {
                final String name = codegen.toEnumVarName(value, "string");
                checks.add(() -> assertFalse(name.isEmpty(), language + " " + value));
                checks.add(() -> assertTrue(
                        Character.isLetter(name.charAt(0)), language + " " + value + " -> " + name));
            }
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("the empty and number-leading enum fallbacks are deterministic")
    void enumConstantFallbacks() {
        final AbstractBetterCodegen java = codegen("java");
        assertEquals("SPACE_1", java.toEnumVarName(" ", "String"));
        assertEquals("SPACE_2", java.toEnumVarName("  ", "String"));
        assertEquals("VALUE_0040_0040", java.toEnumVarName("@@", "String"));
        assertEquals("N1ST", java.toEnumVarName("1st", "String"));
        assertEquals("n1st", codegen("dart").toEnumVarName("1st", "String"));
    }

    @Test
    @DisplayName("a later enum constant that repeats a name gets a numeric suffix")
    void duplicateEnumNamesAreSuffixedInOrder() {
        final List<Map<String, Object>> vars = new ArrayList<>();
        for (final String name : List.of("A_B", "A_B", "C", "C", "A_B_2")) {
            final Map<String, Object> entry = new HashMap<>();
            entry.put("name", name);
            vars.add(entry);
        }
        AbstractBetterCodegen.dedupeEnumNames(vars, "name");
        assertEquals(
                List.of("A_B", "A_B_3", "C", "C2", "A_B_2"),
                vars.stream().map(v -> v.get("name")).collect(Collectors.toList()));
    }

    /* Row: language, raw name, variable name, parameter name. */
    private static List<List<String>> numberLeadingNames() {
        return List.of(
                List.of("java", "2fa", "n2fa", "n2fa"),
                List.of("kotlin", "2fa", "n2fa", "n2fa"),
                List.of("csharp", "2fa", "N2fa", "n2fa"),
                List.of("go", "2fa", "N2fa", "n2fa"),
                List.of("python", "2ndPage", "n2nd_page", "n2nd_page"),
                List.of("ruby", "2ndPage", "n2nd_page", "n2nd_page"),
                List.of("dart", "2fa", "n2fa", "n2fa"),
                List.of("swift", "2fa", "n2fa", "n2fa"),
                List.of("elixir", "2fa", "n2fa", "n2fa"),
                List.of("rust", "2fa", "n2fa", "n2fa"),
                List.of("node", "2fa", "n2fa", "n2fa"),
                List.of("php", "2fa", "n2fa", "n2fa"));
    }

    @Test
    @DisplayName("a name starting with a number gets the letter n, never an escape")
    void numberLeadingNamesGetLetterPrefix() {
        final List<Executable> checks = new ArrayList<>();
        for (final List<String> row : numberLeadingNames()) {
            final AbstractBetterCodegen codegen = codegen(row.get(0));
            checks.add(() -> assertEquals(row.get(2), codegen.toVarName(row.get(1)), row.toString()));
            checks.add(() -> assertEquals(row.get(3), codegen.toParamName(row.get(1)), row.toString()));
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("server enum constants are legal upper-snake identifiers")
    void serverEnumConstantNames() {
        assertEquals("US", AbstractBetterCodegen.serverEnumConstantName("us"));
        assertEquals("A_B", AbstractBetterCodegen.serverEnumConstantName("a&b"));
        assertEquals("IT_S", AbstractBetterCodegen.serverEnumConstantName("it's"));
        assertEquals("EU_WEST_1", AbstractBetterCodegen.serverEnumConstantName("eu-west.1"));
        assertEquals("N2ND", AbstractBetterCodegen.serverEnumConstantName("2nd"));
        assertEquals("VALUE_0026", AbstractBetterCodegen.serverEnumConstantName("&"));
    }

    @Test
    @DisplayName("a server variant name falls back to Server<index> when unusable")
    void serverVariantNames() {
        assertEquals("Mirror", AbstractBetterCodegen.serverVariantName("Mirror", 2, Set.of(), Set.of()));
        assertEquals("Server0", AbstractBetterCodegen.serverVariantName(null, 0, Set.of(), Set.of()));
        assertEquals("Server1", AbstractBetterCodegen.serverVariantName("1st region", 1, Set.of(), Set.of()));
        assertEquals("Server1", AbstractBetterCodegen.serverVariantName("é", 1, Set.of(), Set.of()));
        assertEquals("Server2", AbstractBetterCodegen.serverVariantName("Mirror", 2, Set.of("Mirror"), Set.of()));
        assertEquals("Server1", AbstractBetterCodegen.serverVariantName("Region", 1, Set.of(), Set.of("Region")));
        assertEquals("Server11", AbstractBetterCodegen.serverVariantName(null, 1, Set.of("Server1"), Set.of()));
    }

    private static CodegenServerVariable variable(String name, String... values) {
        final CodegenServerVariable v = new CodegenServerVariable();
        v.name = name;
        v.defaultValue = values[0];
        v.enumValues = List.of(values);
        return v;
    }

    private static CodegenServer server(String description, CodegenServerVariable... vars) {
        final CodegenServer s = new CodegenServer();
        s.url = "https://example.com";
        s.description = description;
        s.variables = List.of(vars);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serverTypeDef(String language, CodegenServer... servers) {
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "getThing";
        op.servers = List.of(servers);
        final Map<String, Object> operations = new HashMap<>();
        codegen(language).enrichOperationServers(List.of(op), operations);
        return Objects.requireNonNull((List<Map<String, Object>>) operations.get("serverTypeDefs")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> vars(Map<String, Object> typeDef, int variant) {
        final List<Map<String, Object>> variants =
                Objects.requireNonNull((List<Map<String, Object>>) typeDef.get("variants"));
        return Objects.requireNonNull(
                (List<Map<String, Object>>) variants.get(variant).get("serverVariables"));
    }

    @Test
    @DisplayName("a variable shared by two variants gets one enum type per variant")
    @SuppressWarnings("unchecked")
    void sharedServerVariableIsQualifiedByVariant() {
        final Map<String, Object> typeDef =
                serverTypeDef(
                        "python",
                        server("Regional", variable("region", "us", "eu", "ap")),
                        server("Mirror", variable("region", "us", "eu"), variable("channel", "a&b", "it's")));
        assertEquals("RegionalRegion", vars(typeDef, 0).get(0).get("enumTypeName"));
        assertEquals("MirrorRegion", vars(typeDef, 1).get(0).get("enumTypeName"));
        assertEquals("Channel", vars(typeDef, 1).get(1).get("enumTypeName"));
        final List<Map<String, Object>> serverEnums =
                Objects.requireNonNull((List<Map<String, Object>>) typeDef.get("serverEnums"));
        assertEquals(
                List.of("RegionalRegion", "MirrorRegion", "Channel"),
                serverEnums.stream().map(e -> e.get("typeName")).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("a variable used by one variant keeps its plain enum type name")
    void unsharedServerVariableKeepsPlainName() {
        final Map<String, Object> typeDef =
                serverTypeDef("java", server("Staging", variable("environment", "sandbox")));
        assertEquals("Environment", vars(typeDef, 0).get(0).get("enumTypeName"));
    }

    @Test
    @DisplayName("server enum values carry legal names and the language's literal")
    @SuppressWarnings("unchecked")
    void serverEnumValuesCarryNamesAndLiterals() {
        final List<Map<String, String>> values =
                Objects.requireNonNull((List<Map<String, String>>)
                        vars(serverTypeDef("python", server("Mirror", variable("channel", "a&b", "it's", "a-b"))), 0)
                                .get(0)
                                .get("enumValues"));
        assertEquals(
                List.of("A_B", "IT_S", "A_B_2"), values.stream().map(v -> v.get("name")).collect(Collectors.toList()));
        assertEquals(
                List.of("'a&b'", "'it\\'s'", "'a-b'"),
                values.stream().map(v -> v.get("literal")).collect(Collectors.toList()));
        assertEquals("it's", values.get(1).get("value"));
    }

  /**
   * The client facade's withToken factory builds a bearer authenticator whatever
   * schemes the spec declares, so every SDK must generate one even for a spec
   * with no security schemes at all.
   */
  @Test
  @DisplayName("every SDK generates a bearer authenticator for a spec without security schemes")
  void bearerAuthenticatorIsGeneratedForASpecWithoutSecuritySchemes() throws IOException {
    final Path work = Path.of("target", "probe-no-security").toAbsolutePath();
    Files.createDirectories(work);
    final Path spec = work.resolve("openapi.yaml");
    Files.writeString(
        spec,
        "openapi: 3.0.3\n"
            + "info:\n  title: Bare\n  version: '1'\n"
            + "paths:\n  /ping:\n    get:\n      operationId: ping\n"
            + "      responses:\n        '200':\n          description: ok\n");
    for (final String generator : ProbeGenerator.ALL_GENERATORS) {
      final Path out = work.resolve(generator);
      ProbeGenerator.generate(
          generator,
          Map.of("packageName", "bare", "skipFormatter", "true"),
          out,
          spec.toString());
      try (Stream<Path> files = Files.walk(out)) {
        assertTrue(
            files.anyMatch(
                f -> f.getFileName().toString().toLowerCase(Locale.ROOT).contains("bearer")
                    && Files.isRegularFile(f)),
            generator + " must generate a bearer authenticator");
      }
    }
  }

  private static ModelsMap modelsMapOf(CodegenModel model) {
    final ModelMap modelMap = new ModelMap();
    modelMap.setModel(model);
    final ModelsMap models = new ModelsMap();
    models.setModels(new ArrayList<>(List.of(modelMap)));
    return models;
  }

  private static CodegenModel unionOf(String name, String... variants) {
    final CodegenModel union = new CodegenModel();
    union.name = name;
    union.classname = name;
    union.oneOf = new java.util.TreeSet<>(List.of(variants));
    union.discriminator = new CodegenDiscriminator();
    final List<CodegenDiscriminator.MappedModel> mapped = new ArrayList<>();
    for (final String variant : variants) {
      mapped.add(new CodegenDiscriminator.MappedModel(variant, variant));
    }
    union.discriminator.setMappedModels(new java.util.LinkedHashSet<>(mapped));
    return union;
  }

  @Test
  @DisplayName("a variant reused by a later union keeps the union that declared it first")
  void aVariantKeepsTheFirstUnionThatClaimsIt() {
    final AbstractBetterCodegen codegen = codegen("java");
    final OpenAPI api = new OpenAPI().components(new Components());
    // Declaration order: PetFood first, then DeprecatedFood (which sorts first by name).
    api.getComponents().addSchemas("PetFood", new Schema<>());
    api.getComponents().addSchemas("DeprecatedFood", new Schema<>());
    codegen.setOpenAPI(api);
    final CodegenModel dry = new CodegenModel();
    dry.name = "DryFood";
    dry.classname = "DryFood";
    final Map<String, ModelsMap> all = new java.util.TreeMap<>();
    all.put("DeprecatedFood", modelsMapOf(unionOf("DeprecatedFood", "DryFood")));
    all.put("PetFood", modelsMapOf(unionOf("PetFood", "DryFood")));
    all.put("DryFood", modelsMapOf(dry));

    codegen.wireDiscriminatorParents(all);

    assertEquals("PetFood", dry.parent);
    assertFalse(AbstractBetterCodegen.setParentOnChild(all, "DryFood", "DeprecatedFood"));
    assertEquals("PetFood", dry.parent);
  }

  @Test
  @DisplayName("additionalProperties of a typed schema fills additionalPropertiesType when opted in")
  void typedAdditionalPropertiesFillTheModelType() {
    final Schema<?> typed = new Schema<>().type("object");
    typed.setAdditionalProperties(new StringSchema());
    final Schema<?> untyped = new Schema<>().type("object");
    untyped.setAdditionalProperties(true);
    final AbstractBetterCodegen ruby = codegen("ruby");
    ruby.setOpenAPI(new OpenAPI().components(new Components()));

    assertEquals("String", ruby.additionalPropertiesValueType(typed));
    assertEquals(null, ruby.additionalPropertiesValueType(untyped));
    assertTrue(ruby.populatesAdditionalPropertiesType());
    assertFalse(codegen("java").populatesAdditionalPropertiesType());
  }

  @Test
  @DisplayName("a default written next to a $ref reaches the property")
  void aRefSiblingDefaultIsCarriedOntoTheProperty() {
    final StringSchema availability = new StringSchema();
    availability.setEnum(List.of("available", "on-hold"));
    final AbstractBetterCodegen codegen = codegen("java");
    codegen.setOpenAPI(
        new OpenAPI().components(new Components().addSchemas("Availability", availability)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Availability");
    reference.setDefault("on-hold");

    final CodegenProperty property = codegen.fromProperty("availability", reference, false, false);

    assertEquals("on-hold", property.vendorExtensions.get("siblingDefault"));
    assertEquals(true, property.vendorExtensions.get("siblingDefaultIsEnum"));
    assertEquals("Availability", property.vendorExtensions.get("siblingDefaultEnumType"));
    assertEquals(
        codegen.toEnumVarName("on-hold", "String"),
        property.vendorExtensions.get("siblingDefaultEnumVar"));
  }

  @Test
  @DisplayName("a default on the allOf wrapper the parser leaves around a $ref reaches the property")
  void aWrappedRefSiblingDefaultIsCarriedOntoTheProperty() {
    final StringSchema availability = new StringSchema();
    availability.setEnum(List.of("available", "on-hold"));
    final AbstractBetterCodegen codegen = codegen("java");
    codegen.setOpenAPI(
        new OpenAPI().components(new Components().addSchemas("Availability", availability)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Availability");
    final Schema<?> wrapper = new Schema<>();
    wrapper.addAllOfItem(reference);
    wrapper.setDefault("on-hold");

    final CodegenProperty property = codegen.fromProperty("availability", wrapper, false, false);

    assertEquals("on-hold", property.vendorExtensions.get("siblingDefault"));
    assertEquals(true, property.vendorExtensions.get("siblingDefaultIsEnum"));
    assertEquals("Availability", property.vendorExtensions.get("siblingDefaultEnumType"));
  }

  @Test
  @DisplayName("QUERY is a safe and idempotent method, POST and PATCH are neither")
  void httpMethodClassificationIsShared() {
    for (final String safe : List.of("GET", "HEAD", "OPTIONS", "TRACE", "QUERY", "query")) {
      assertTrue(AbstractBetterCodegen.isSafeHttpMethod(safe), safe);
      assertTrue(AbstractBetterCodegen.isIdempotentHttpMethod(safe), safe);
    }
    for (final String idempotent : List.of("PUT", "DELETE")) {
      assertFalse(AbstractBetterCodegen.isSafeHttpMethod(idempotent), idempotent);
      assertTrue(AbstractBetterCodegen.isIdempotentHttpMethod(idempotent), idempotent);
    }
    for (final String unsafe : List.of("POST", "PATCH", "PURGE")) {
      assertFalse(AbstractBetterCodegen.isSafeHttpMethod(unsafe), unsafe);
      assertFalse(AbstractBetterCodegen.isIdempotentHttpMethod(unsafe), unsafe);
    }
    assertFalse(AbstractBetterCodegen.isSafeHttpMethod(null));
  }

  @Test
  @DisplayName("a parameter named like an injected argument is suffixed, others are untouched")
  void injectedArgumentNamesAreSuffixedByTheSharedToParamName() {
    assertEquals("optionsParam", codegen("csharp").toParamName("options"));
    assertEquals("cancellationTokenParam", codegen("csharp").toParamName("cancellationToken"));
    assertEquals("requestOptionsParam", codegen("node").toParamName("requestOptions"));
    assertEquals("opts_param", codegen("elixir").toParamName("opts"));
    assertEquals("api_param", codegen("elixir").toParamName("api"));
    assertEquals("optionsParam", codegen("java").toParamName("options"));
    assertEquals("petId", codegen("java").toParamName("pet_id"));
    assertEquals("petId", codegen("csharp").toParamName("pet_id"));
  }

  @Test
  @DisplayName("a scheme with several OAuth2 flows names the dropped ones")
  void droppedOAuthFlowsAreNamed() {
    final io.swagger.v3.oas.models.security.OAuthFlows flows =
        new io.swagger.v3.oas.models.security.OAuthFlows()
            .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow())
            .clientCredentials(new io.swagger.v3.oas.models.security.OAuthFlow());
    final io.swagger.v3.oas.models.security.SecurityScheme scheme =
        new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
            .flows(flows);

    final List<String> declared = AbstractBetterCodegen.declaredOAuthFlows(scheme);

    assertEquals(List.of("clientCredentials", "authorizationCode"), declared);
    assertEquals(
        "OAuth2 security scheme 'sso' declares the flows clientCredentials, authorizationCode;"
            + " only clientCredentials gets an authenticator, the flows authorizationCode"
            + " are dropped",
        AbstractBetterCodegen.droppedOAuthFlowsMessage("sso", declared));
  }

  @Test
  @DisplayName("an http scheme other than basic and bearer is named in the warning")
  void unsupportedHttpSchemeIsNamed() {
    final io.swagger.v3.oas.models.security.SecurityScheme digest =
        new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
            .scheme("digest");
    assertEquals(
        "Unsupported http security scheme 'digest' for 'legacy': only basic and bearer get an"
            + " authenticator",
        AbstractBetterCodegen.unsupportedSchemeMessage("legacy", digest));
    assertEquals(null, codegen("java").buildSchemeAuthSpec("legacy", digest));
  }

  @Test
  @DisplayName("every SDK exposes one environment variable per credential field")
  void environmentVariableNamesAreExposedToEveryLanguage() {
    final io.swagger.v3.oas.models.security.SecurityScheme bearer =
        new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
            .scheme("bearer");
    final io.swagger.v3.oas.models.security.SecurityScheme key =
        new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
            .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
            .name("X-Key");
    for (final String language : CODEGENS.keySet()) {
      final AbstractBetterCodegen codegen = codegen(language);
      codegen.additionalProperties().put("envVarPrefix", "Acme SDK");
      codegen.processOpts();
      codegen.processOpenAPI(
          new OpenAPI()
              .info(new io.swagger.v3.oas.models.info.Info().title("Acme").version("1"))
              .components(
                  new Components().addSecuritySchemes("tok", bearer).addSecuritySchemes("key", key)));

      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> vars =
          (List<Map<String, Object>>)
              Objects.requireNonNull(codegen.additionalProperties().get("securitySchemeEnvVars"));
      assertEquals(
          List.of("ACME_SDK_API_KEY", "ACME_SDK_TOKEN"),
          vars.stream().map(v -> String.valueOf(v.get("envName"))).collect(Collectors.toList()),
          language);
      assertEquals("key", vars.get(0).get("schemeId"), language);
      assertEquals("apiKey", vars.get(0).get("field"), language);
    }
  }
}
