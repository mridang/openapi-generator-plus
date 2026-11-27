package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.io.TempDir;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenParameter;
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

  /**
   * OpenAPI 3.1 makes {@code paths} optional, so a document may hold only
   * components. Every SDK must still generate its models from one.
   */
  @Test
  @DisplayName("every SDK generates the models of a document without paths")
  void everySdkGeneratesTheModelsOfADocumentWithoutPaths() throws IOException {
    final Path work = Path.of("target", "probe-no-paths").toAbsolutePath();
    Files.createDirectories(work);
    final Path spec = work.resolve("openapi.yaml");
    Files.writeString(
        spec,
        "openapi: 3.1.0\n"
            + "info:\n  title: Bare\n  version: '1'\n"
            + "components:\n  schemas:\n    Gadget:\n      type: object\n"
            + "      properties:\n        code:\n          type: string\n");
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
                f -> f.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("gadget")
                    && Files.isRegularFile(f)),
            generator + " must generate the Gadget model");
      }
    }
  }

  /**
   * A document without {@code servers} means a single server whose URL is
   * {@code /}, relative to where the document is served. An SDK has no such
   * location, so every SDK falls back to the same default base URL,
   * {@code http://localhost}, which the caller is expected to replace.
   */
  @Test
  @DisplayName("every SDK gives a document without servers the same default base URL")
  void everySdkGivesADocumentWithoutServersTheSameDefaultBaseUrl() throws IOException {
    final Path work = Path.of("target", "probe-no-servers").toAbsolutePath();
    Files.createDirectories(work);
    final Path spec = work.resolve("openapi.yaml");
    Files.writeString(
        spec,
        "openapi: 3.1.0\n"
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
            files.filter(Files::isRegularFile)
                .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT)
                    .matches("configuration\\.[a-z]+"))
                .anyMatch(f -> read(f).contains("http://localhost")),
            generator + " must default the base URL of a server-less document to http://localhost");
      }
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /**
   * OpenAPI 3.0 marks a schema as accepting null with {@code nullable: true}
   * and writes exclusive bounds as booleans. The probe fixture's {@code Gauge}
   * carries both, and every SDK must read them as the 3.1 forms are read.
   */
  @Test
  @DisplayName("every SDK reads the OAS 3.0 nullable keyword and boolean exclusive bounds")
  void everySdkReadsTheOas30NullableKeywordAndBooleanExclusiveBounds() throws IOException {
    final String text =
        new String(
            Objects.requireNonNull(
                    AbstractBetterCodegenTest.class
                        .getClassLoader()
                        .getResourceAsStream("specs/probe/openapi.yaml"))
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    final io.swagger.v3.parser.core.models.ParseOptions options =
        new io.swagger.v3.parser.core.models.ParseOptions();
    options.setResolve(true);
    final OpenAPI api =
        new io.swagger.parser.OpenAPIParser().readContents(text, null, options).getOpenAPI();
    final Schema<?> gauge =
        Objects.requireNonNull(api.getComponents().getSchemas().get("Gauge"));
    for (final String language : CODEGENS.keySet()) {
      final AbstractBetterCodegen codegen = codegen(language);
      codegen.setOpenAPI(api);
      final CodegenModel model = codegen.fromModel("Gauge", gauge);
      final Map<String, CodegenProperty> vars =
          model.vars.stream().collect(Collectors.toMap(v -> v.baseName, v -> v));
      for (final String name : List.of("needle", "dial", "bezel", "crown")) {
        assertTrue(Objects.requireNonNull(vars.get(name)).isNullable,
            language + " " + name + " must be nullable");
      }
      assertTrue(Objects.requireNonNull(vars.get("needle")).required, language);
      assertTrue(Objects.requireNonNull(vars.get("dial")).required, language);
      assertFalse(Objects.requireNonNull(vars.get("bezel")).required, language);
      assertFalse(Objects.requireNonNull(vars.get("crown")).required, language);
      final CodegenProperty span = Objects.requireNonNull(vars.get("span"));
      assertFalse(span.isNullable, language);
      assertTrue(span.exclusiveMinimum, language);
      assertTrue(span.exclusiveMaximum, language);
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
  @DisplayName("a classic allOf base and its children are marked, and the inherited properties flagged")
  void aClassicAllOfBaseAndItsChildrenAreMarked() {
    final CodegenProperty id = new CodegenProperty();
    id.baseName = "id";
    final CodegenProperty extra = new CodegenProperty();
    extra.baseName = "extra";
    final CodegenModel base = unionOf("Base", "Child");
    base.oneOf = new java.util.TreeSet<>();
    base.vars = new ArrayList<>(List.of(id));
    final CodegenProperty childId = new CodegenProperty();
    childId.baseName = "id";
    final CodegenModel child = new CodegenModel();
    child.name = "Child";
    child.classname = "Child";
    child.parent = "Base";
    child.vars = new ArrayList<>(List.of(childId, extra));
    final Map<String, ModelsMap> all = new java.util.TreeMap<>();
    all.put("Base", modelsMapOf(base));
    all.put("Child", modelsMapOf(child));

    codegen("java").markClassicDiscriminatorInheritance(all);

    assertAll(
        () -> assertTrue(AbstractBetterCodegen.isClassicDiscriminatorBase(base)),
        () -> assertEquals(true, base.vendorExtensions.get("x-allof-base")),
        () -> assertEquals(true, child.vendorExtensions.get("x-allof-child")),
        () -> assertEquals(true, childId.vendorExtensions.get("x-inherited")),
        () -> assertEquals(null, extra.vendorExtensions.get("x-inherited")),
        () -> assertEquals("Base", child.vendorExtensions.get("x-allof-base-name")));
  }

  @Test
  @DisplayName("a oneOf union is not a classic allOf base")
  void aUnionIsNotAClassicAllOfBase() {
    assertFalse(AbstractBetterCodegen.isClassicDiscriminatorBase(unionOf("PetFood", "DryFood")));
  }

  private static Schema<Object> objectSchema() {
    final Schema<Object> object = new Schema<>();
    object.setType("object");
    return object;
  }

  private static Schema<Object> typed(String type) {
    final Schema<Object> schema = new Schema<>();
    schema.setType(type);
    return schema;
  }

  @SuppressWarnings("unchecked")
  private static Schema<Object> refTo(String name) {
    return new Schema<>().$ref("#/components/schemas/" + name);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<Object> oneOfSchema(Schema<?>... variants) {
    final Schema<Object> union = new Schema<>();
    union.setOneOf(new ArrayList<>(List.of(variants)));
    return union;
  }

  /** A spec whose components are the given named schemas. */
  private static OpenAPI specWith(Map<String, ? extends Schema<?>> schemas) {
    final OpenAPI api = new OpenAPI().components(new Components());
    schemas.forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
    return api;
  }

  private static final String DECODE_SUFFIX =
      ". A payload that matches several variants decodes as the first declared one";

  @Test
  @DisplayName("a oneOf of two object variants without a discriminator is named in one warning")
  void ambiguousOneOfNamesItsObjectVariants() {
    final OpenAPI api = specWith(Map.of(
        "Pet", oneOfSchema(refTo("Cat"), refTo("Dog")),
        "Cat", objectSchema(),
        "Dog", objectSchema()));

    assertEquals(
        List.of("Ambiguous oneOf 'Pet' has no discriminator: Cat and Dog both accept a JSON object"
            + DECODE_SUFFIX),
        AbstractBetterCodegen.ambiguousOneOfMessages(api));
  }

  @Test
  @DisplayName("a oneOf with a discriminator is not ambiguous")
  void discriminatedOneOfIsNotAmbiguous() {
    final Schema<Object> union = oneOfSchema(refTo("Cat"), refTo("Dog"));
    union.setDiscriminator(new io.swagger.v3.oas.models.media.Discriminator()
        .propertyName("kind"));
    final OpenAPI api = specWith(Map.of(
        "Pet", union,
        "Cat", objectSchema(),
        "Dog", objectSchema()));

    assertTrue(AbstractBetterCodegen.ambiguousOneOfMessages(api).isEmpty());
  }

  @Test
  @DisplayName("variants that accept different JSON types are not ambiguous")
  void distinctJsonTypesAreNotAmbiguous() {
    final OpenAPI api = specWith(Map.of(
        "Id", oneOfSchema(typed("string"), typed("integer")),
        "Other", oneOfSchema(refTo("Thing"), typed("boolean")),
        "Thing", objectSchema()));

    assertTrue(AbstractBetterCodegen.ambiguousOneOfMessages(api).isEmpty());
  }

  @Test
  @DisplayName("an inline object variant is named by its position in the oneOf")
  void inlineVariantsAreNamedByPosition() {
    final OpenAPI api = specWith(Map.of(
        "Blob", oneOfSchema(objectSchema(), objectSchema(), typed("string"))));

    assertEquals(
        List.of("Ambiguous oneOf 'Blob' has no discriminator: inline variant 1 and inline variant 2"
            + " both accept a JSON object" + DECODE_SUFFIX),
        AbstractBetterCodegen.ambiguousOneOfMessages(api));
  }

  @Test
  @DisplayName("the warning pass leaves the declared variant order untouched")
  void ambiguousOneOfKeepsDeclarationOrder() {
    final Schema<Object> union = oneOfSchema(refTo("Dog"), refTo("Cat"));
    final OpenAPI api = specWith(Map.of(
        "Pet", union,
        "Cat", objectSchema(),
        "Dog", objectSchema()));

    assertEquals(1, AbstractBetterCodegen.ambiguousOneOfMessages(api).size());
    assertEquals("#/components/schemas/Dog", union.getOneOf().get(0).get$ref());
    assertEquals("#/components/schemas/Cat", union.getOneOf().get(1).get$ref());
  }

  @Test
  @DisplayName("a oneOf declared as a property of a schema is checked and named by its path")
  void ambiguousOneOfInPropertyIsNamedByPath() {
    final Schema<Object> owner = objectSchema();
    owner.addProperty("pet", oneOfSchema(refTo("Cat"), refTo("Dog")));
    final OpenAPI api = specWith(Map.of(
        "Owner", owner,
        "Cat", objectSchema(),
        "Dog", objectSchema()));

    assertEquals(
        List.of("Ambiguous oneOf 'Owner.pet' has no discriminator: Cat and Dog both accept a JSON"
            + " object" + DECODE_SUFFIX),
        AbstractBetterCodegen.ambiguousOneOfMessages(api));
  }

  @Test
  @DisplayName("a typed additionalProperties fills the model type and hands over a value node")
  void typedAdditionalPropertiesFillTheModelType() {
    final Schema<?> typed = new Schema<>().type("object");
    typed.setAdditionalProperties(new StringSchema());
    final Schema<?> untyped = new Schema<>().type("object");
    untyped.setAdditionalProperties(true);
    final Schema<?> empty = new Schema<>().type("object");
    empty.setAdditionalProperties(new Schema<>());
    for (final String language : List.of("java", "kotlin", "csharp", "php", "python", "ruby",
        "node", "dart", "go", "rust", "swift", "elixir")) {
      final AbstractBetterCodegen codegen = codegen(language);
      codegen.setOpenAPI(new OpenAPI().components(new Components()
          .addSchemas("Typed", typed).addSchemas("Untyped", untyped).addSchemas("Empty", empty)));

      assertEquals(codegen.getTypeDeclaration(new StringSchema()),
          codegen.additionalPropertiesValueType(typed), language);
      assertEquals(null, codegen.additionalPropertiesValueType(untyped), language);
      assertEquals(null, codegen.additionalPropertiesValueType(empty), language);

      final CodegenModel model = codegen.fromModel("Typed", typed);
      assertEquals(true, model.vendorExtensions.get("hasTypedAdditionalProperties"), language);
      assertTrue(model.vendorExtensions.get("additionalPropertiesValue") instanceof CodegenProperty,
          language);
      assertEquals(codegen.getTypeDeclaration(new StringSchema()),
          model.additionalPropertiesType, language);
      final Schema<?> nullableValues = new Schema<>().type("object");
      nullableValues.setAdditionalProperties(new StringSchema().nullable(true));
      final CodegenModel nullableModel = codegen.fromModel("Nullable", nullableValues);
      final Object nullableNode = nullableModel.vendorExtensions.get("additionalPropertiesValue");
      assertTrue(nullableNode instanceof CodegenProperty && ((CodegenProperty) nullableNode).isNullable,
          language);
      for (final String name : List.of("Untyped", "Empty")) {
        final CodegenModel free = codegen.fromModel(name, name.equals("Empty") ? empty : untyped);
        assertEquals(null, free.vendorExtensions.get("additionalPropertiesValue"), language);
        assertEquals(null, free.vendorExtensions.get("hasTypedAdditionalProperties"), language);
      }
    }
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
  @DisplayName("a default the referenced enum component declares reaches the property")
  void aComponentEnumDefaultIsCarriedOntoTheProperty() {
    final StringSchema swatch = new StringSchema();
    swatch.setEnum(List.of("red", "green", "blue"));
    swatch.setDefault("green");
    final AbstractBetterCodegen codegen = codegen("java");
    codegen.setOpenAPI(new OpenAPI().components(new Components().addSchemas("Swatch", swatch)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Swatch");

    final CodegenProperty property = codegen.fromProperty("swatch", reference, false, false);

    assertEquals("green", property.vendorExtensions.get("siblingDefault"));
    assertEquals(true, property.vendorExtensions.get("siblingDefaultIsEnum"));
    assertEquals("Swatch", property.vendorExtensions.get("siblingDefaultEnumType"));
    assertEquals("GREEN", property.vendorExtensions.get("siblingDefaultEnumVar"));
  }

  @Test
  @DisplayName("a default written next to a $ref wins over the enum component's own default")
  void aRefSiblingDefaultWinsOverTheComponentDefault() {
    final StringSchema swatch = new StringSchema();
    swatch.setEnum(List.of("red", "green", "blue"));
    swatch.setDefault("green");
    final AbstractBetterCodegen codegen = codegen("java");
    codegen.setOpenAPI(new OpenAPI().components(new Components().addSchemas("Swatch", swatch)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Swatch");
    reference.setDefault("blue");

    final CodegenProperty property = codegen.fromProperty("swatch", reference, false, false);

    assertEquals("blue", property.vendorExtensions.get("siblingDefault"));
  }

  @Test
  @DisplayName("a $ref to a schema that is not an enum takes no default from the component")
  void aNonEnumComponentDefaultIsNotCarried() {
    final StringSchema label = new StringSchema();
    label.setDefault("untitled");
    final AbstractBetterCodegen codegen = codegen("java");
    codegen.setOpenAPI(new OpenAPI().components(new Components().addSchemas("Label", label)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Label");

    final CodegenProperty property = codegen.fromProperty("label", reference, false, false);

    assertEquals(null, property.vendorExtensions.get("siblingDefault"));
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

    private static io.swagger.v3.oas.models.OpenAPI securedSpec(
            @javax.annotation.Nullable final List<io.swagger.v3.oas.models.security.SecurityRequirement> global) {
        final io.swagger.v3.oas.models.Components components = new Components();
        components.addSecuritySchemes("zKey", new io.swagger.v3.oas.models.security.SecurityScheme()
                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
                .name("X-Key"));
        components.addSecuritySchemes("aBearer", new io.swagger.v3.oas.models.security.SecurityScheme()
                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                .scheme("bearer"));
        components.addSecuritySchemes("mTls", new io.swagger.v3.oas.models.security.SecurityScheme()
                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.MUTUALTLS));
        final io.swagger.v3.oas.models.OpenAPI spec = new OpenAPI().components(components);
        spec.setSecurity(global);
        return spec;
    }

    private static io.swagger.v3.oas.models.security.SecurityRequirement requirement(
            final String... schemes) {
        final io.swagger.v3.oas.models.security.SecurityRequirement requirement =
                new io.swagger.v3.oas.models.security.SecurityRequirement();
        for (final String scheme : schemes) {
            requirement.addList(scheme);
        }
        return requirement;
    }

    private static List<String> schemeIds(final List<Map<String, Object>> order) {
        final List<String> ids = new ArrayList<>();
        for (final Map<String, Object> entry : order) {
            ids.add((String) entry.get("schemeId"));
        }
        return ids;
    }

    @Test
    @DisplayName("an operation's accepted schemes follow its own security order, not the components'")
    void securitySchemeOrderFollowsTheOperation() {
        final io.swagger.v3.oas.models.OpenAPI spec = securedSpec(null);
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setSecurity(List.of(requirement("zKey"), requirement("aBearer")));
        final List<Map<String, Object>> order = codegen("java").securitySchemeOrder(operation, spec);
        assertAll(
                () -> assertEquals(List.of("zKey", "aBearer"), schemeIds(order)),
                () -> assertEquals("zKey", order.get(0).get("paramName")),
                () -> assertEquals("aBearer", order.get(1).get("paramName")));
    }

    @Test
    @DisplayName("an operation that declares no security inherits the global order")
    void securitySchemeOrderFallsBackToGlobal() {
        final io.swagger.v3.oas.models.OpenAPI spec =
                securedSpec(List.of(requirement("zKey"), requirement("aBearer")));
        assertEquals(
                List.of("zKey", "aBearer"),
                schemeIds(codegen("go").securitySchemeOrder(new io.swagger.v3.oas.models.Operation(), spec)));
    }

    @Test
    @DisplayName("security: [] on the operation yields no scheme, even under a global requirement")
    void securitySchemeOrderIsEmptyForNoSecurity() {
        final io.swagger.v3.oas.models.OpenAPI spec = securedSpec(List.of(requirement("zKey")));
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setSecurity(new ArrayList<>());
        final io.swagger.v3.oas.models.Operation inheriting = new io.swagger.v3.oas.models.Operation();
        assertAll(
                () -> assertTrue(codegen("java").securitySchemeOrder(operation, spec).isEmpty()),
                () -> assertTrue(codegen("java")
                        .securitySchemeOrder(inheriting, securedSpec(new ArrayList<>())).isEmpty()),
                () -> assertTrue(codegen("java").securitySchemeOrder(inheriting, securedSpec(null)).isEmpty()));
    }

    @Test
    @DisplayName("a scheme named in several alternatives or an AND group is listed once, at its first position")
    void securitySchemeOrderListsEachSchemeOnce() {
        final io.swagger.v3.oas.models.OpenAPI spec = securedSpec(null);
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setSecurity(List.of(
                requirement("zKey", "aBearer"), requirement("aBearer"), requirement("zKey")));
        assertEquals(
                List.of("zKey", "aBearer"),
                schemeIds(codegen("python").securitySchemeOrder(operation, spec)));
    }

    @Test
    @DisplayName("a scheme the client holds no credential for is left out of the order")
    void securitySchemeOrderSkipsUnheldSchemes() {
        final io.swagger.v3.oas.models.OpenAPI spec = securedSpec(null);
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setSecurity(List.of(
                requirement("mTls"), requirement("missing"), requirement("aBearer")));
        assertEquals(
                List.of("aBearer"),
                schemeIds(codegen("ruby").securitySchemeOrder(operation, spec)));
    }

    @Test
    @DisplayName("the client takes one credential per supported scheme, in component order")
    void clientSchemesListsEachSupportedScheme() {
        final List<Map<String, Object>> schemes = codegen("kotlin").schemeCredentials(securedSpec(null));
        assertAll(
                () -> assertEquals(List.of("zKey", "aBearer"), schemeIds(schemes)),
                () -> assertEquals("zKey", schemes.get(0).get("paramName")),
                () -> assertEquals("ZKeyAuthenticator", schemes.get(0).get("className")),
                () -> assertEquals("ABearerAuthenticator", schemes.get(1).get("className")));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("fromOperation exposes the order and the has-flag on the op decorator")
    void fromOperationExposesSecuritySchemeOrder() {
        final AbstractBetterCodegen codegen = codegen("java");
        final io.swagger.v3.oas.models.OpenAPI spec = securedSpec(null);
        codegen.setOpenAPI(spec);
        final io.swagger.v3.oas.models.Operation secured = new io.swagger.v3.oas.models.Operation();
        secured.setOperationId("listItems");
        secured.setSecurity(List.of(requirement("zKey"), requirement("aBearer")));
        secured.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        final io.swagger.v3.oas.models.Operation open = new io.swagger.v3.oas.models.Operation();
        open.setOperationId("health");
        open.setSecurity(new ArrayList<>());
        open.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        final Map<String, Object> securedOp = Objects.requireNonNull((Map<String, Object>) codegen
                .fromOperation("/items", "get", secured, new ArrayList<>()).vendorExtensions.get("op"));
        final Map<String, Object> openOp = Objects.requireNonNull((Map<String, Object>) codegen
                .fromOperation("/health", "get", open, new ArrayList<>()).vendorExtensions.get("op"));
        assertAll(
                () -> assertTrue(Objects.requireNonNull((Boolean) securedOp.get("hasSecuritySchemes"))),
                () -> assertEquals(
                        List.of("zKey", "aBearer"),
                        schemeIds(Objects.requireNonNull((List<Map<String, Object>>) securedOp.get("securitySchemeOrder")))),
                () -> assertFalse(Objects.requireNonNull((Boolean) openOp.get("hasSecuritySchemes"))),
                () -> assertTrue(Objects.requireNonNull((List<?>) openOp.get("securitySchemeOrder")).isEmpty()));
    }

    @Test
    @DisplayName("marks a scalar ipv4, ipv6 or string decimal property and leaves a container and a number alone")
    void marksNativeFormats() {
        final CodegenProperty ipv4 = formatted("ipv4", "string", false);
        final CodegenProperty ipv6 = formatted("ipv6", "string", false);
        final CodegenProperty decimalString = formatted("decimal", "string", false);
        final CodegenProperty decimalNumber = formatted("decimal", "number", false);
        final CodegenProperty container = formatted("ipv6", "string", true);
        for (final CodegenProperty property : List.of(ipv4, ipv6, decimalString, decimalNumber, container)) {
            AbstractBetterCodegen.markNativeFormats(property);
        }
        assertAll(
                () -> assertEquals(true, ipv4.vendorExtensions.get("isIpv4")),
                () -> assertEquals(false, ipv4.vendorExtensions.get("isIpv6")),
                () -> assertEquals(true, ipv6.vendorExtensions.get("isIpv6")),
                () -> assertEquals(true, decimalString.vendorExtensions.get("isDecimalString")),
                () -> assertEquals(false, decimalNumber.vendorExtensions.get("isDecimalString")),
                () -> assertFalse(container.vendorExtensions.containsKey("isIpv6")));
    }

    private static CodegenProperty formatted(String format, String openApiType, boolean container) {
        final CodegenProperty property = new CodegenProperty();
        property.dataFormat = format;
        property.openApiType = openApiType;
        property.isContainer = container;
        return property;
    }

    /* The enum member names and descriptions an author supplies (issues 149 and 150). */

    private static List<Map<String, Object>> enumVars(
            String language, List<Object> values, Map<String, Object> extensions, String datatype) {
        final AbstractBetterCodegen codegen = codegen(language);
        final List<Map<String, Object>> vars = codegen.buildEnumVars(values, datatype);
        codegen.postProcessEnumVars(vars);
        codegen.updateEnumVarsWithExtensions(vars, extensions, datatype);
        return vars;
    }

    private static List<String> names(List<Map<String, Object>> vars) {
        return vars.stream().map(v -> String.valueOf(v.get("name"))).collect(Collectors.toList());
    }

    private static Map<String, Object> ext(String key, Object value) {
        final Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    @DisplayName("each spelling of the names key names the members, by array")
    void everyNameSpellingAppliesAnArray() {
        for (final String key : List.of("x-enum-varnames", "x-enumNames", "x-enumnames")) {
            assertEquals(
                    List.of("SMALL", "MEDIUM"),
                    names(enumVars("java", List.of("s", "m"), ext(key, List.of("Small", "Medium")), "String")),
                    key);
        }
    }

    @Test
    @DisplayName("each spelling of the descriptions key sets enumDescription")
    void everyDescriptionSpellingApplies() {
        for (final String key :
                List.of("x-enum-descriptions", "x-enumDescriptions", "x-enumdescriptions")) {
            final List<Map<String, Object>> vars =
                    enumVars("java", List.of("s", "m"), ext(key, List.of("One.", "Two.")), "String");
            assertEquals("One.", vars.get(0).get("enumDescription"), key);
            assertEquals("Two.", vars.get(1).get("enumDescription"), key);
        }
    }

    @Test
    @DisplayName("an object of names applies by wire value and derives the members it does not list")
    void objectNamesApplyByWireValue() {
        final Map<String, Object> names = new java.util.LinkedHashMap<>();
        names.put("b", "Second One");
        assertEquals(
                List.of("A", "SECOND_ONE", "C"),
                names(enumVars("java", List.of("a", "b", "c"), ext("x-enumnames", names), "String")));
    }

    @Test
    @DisplayName("an integer object key matches the integer wire value")
    void integerObjectKeyMatches() {
        final Map<String, Object> names = new java.util.LinkedHashMap<>();
        names.put("2", "Mid");
        final List<Map<String, Object>> vars =
                enumVars("java", List.of(1, 2, 3), ext("x-enumNames", names), "Integer");
        assertEquals("MID", vars.get(1).get("name"));
        assertEquals("2", vars.get(1).get("wire"));
    }

    @Test
    @DisplayName("the first spelling present wins as a whole")
    void firstSpellingWins() {
        final Map<String, Object> both = new java.util.LinkedHashMap<>();
        both.put("x-enumnames", List.of("Ignored"));
        both.put("x-enum-varnames", List.of("Winner"));
        assertEquals("WINNER", enumVars("java", List.of("a"), both, "String").get(0).get("name"));
    }

    @Test
    @DisplayName("an array shorter or longer than the enum applies where it can")
    void arrayLengthMismatch() {
        assertEquals(
                List.of("FIRST", "B"),
                names(enumVars("java", List.of("a", "b"), ext("x-enumNames", List.of("First")), "String")));
        assertEquals(
                List.of("FIRST"),
                names(enumVars("java", List.of("a"), ext("x-enumNames", List.of("First", "Extra")), "String")));
    }

    @Test
    @DisplayName("a null, blank or non-text entry means not supplied")
    void unsuppliedEntriesKeepTheDerivedName() {
        final List<Object> entries = new ArrayList<>();
        entries.add(null);
        entries.add("  ");
        entries.add(7);
        assertEquals(
                List.of("A", "B", "C"),
                names(enumVars("java", List.of("a", "b", "c"), ext("x-enumNames", entries), "String")));
    }

    @Test
    @DisplayName("names align with the schema's enum array when a nullable enum lists null")
    void nullEntryKeepsTheSpecIndex() {
        final List<Object> values = new ArrayList<>();
        values.add("a");
        values.add(null);
        values.add("b");
        assertEquals(
                List.of("ALPHA", "BETA"),
                names(enumVars("java", values, ext("x-enumNames", java.util.Arrays.asList("Alpha", "Ignored", "Beta")), "String")));
    }

    @Test
    @DisplayName("a supplied name is cleaned and cased like a derived one, per language")
    void suppliedNameIsCleanedAndCased() {
        assertEquals("ON_HOLD", enumVars("java", List.of("x"), ext("x-enumNames", List.of("on hold")), "String").get(0).get("name"));
        assertEquals("OnHold", enumVars("rust", List.of("x"), ext("x-enumNames", List.of("on hold")), "String").get(0).get("name"));
        assertEquals("onHold", enumVars("dart", List.of("x"), ext("x-enumNames", List.of("on hold")), "String").get(0).get("name"));
        assertEquals("N1ST", enumVars("java", List.of("x"), ext("x-enumNames", List.of("1st")), "String").get(0).get("name"));
    }

    @Test
    @DisplayName("a supplied name with no letter or digit keeps the derived name")
    void emptyCleanNameKeepsTheDerivedName() {
        assertEquals("X", enumVars("java", List.of("x"), ext("x-enumNames", List.of("@@")), "String").get(0).get("name"));
    }

    @Test
    @DisplayName("two supplied names equal after casing are made distinct and keep their wires")
    void collidingSuppliedNamesGetSuffixes() {
        final List<Map<String, Object>> vars =
                enumVars("java", List.of("p", "q"), ext("x-enumNames", List.of("Active", "active")), "String");
        assertEquals(List.of("ACTIVE", "ACTIVE2"), names(vars));
        assertEquals("q", vars.get(1).get("wire"));
    }

    @Test
    @DisplayName("enumDescription is absent for blank and null entries")
    void blankDescriptionIsNotWritten() {
        final List<Object> entries = new ArrayList<>();
        entries.add("");
        entries.add(null);
        entries.add("  ");
        for (final Map<String, Object> var :
                enumVars("java", List.of("a", "b", "c"), ext("x-enumDescriptions", entries), "String")) {
            assertFalse(var.containsKey("enumDescription"), String.valueOf(var));
        }
    }

    @Test
    @DisplayName("without any key the members and entries are exactly the derived ones")
    void noKeysChangesNothing() {
        for (final String lang : CODEGENS.keySet()) {
            final List<Map<String, Object>> vars =
                    enumVars(lang, List.of("on-hold", "b"), new HashMap<>(), "String");
            assertEquals(codegen(lang).toEnumVarName("on-hold", "String"), vars.get(0).get("name"), lang);
            assertFalse(vars.get(0).containsKey("enumDescription"), lang);
            assertFalse(vars.get(0).containsKey("nameSupplied"), lang);
        }
    }

    @Test
    @DisplayName("enumMemberName agrees with the declared members for the same schema")
    void enumMemberNameAgreesWithTheDeclaredMembers() {
        final Schema<String> schema = new StringSchema();
        schema.setEnum(List.of("paused", "active", "live"));
        final Map<String, Object> names = new java.util.LinkedHashMap<>();
        names.put("paused", "on hold");
        names.put("active", "Active");
        names.put("live", "active");
        schema.addExtension("x-enumnames", names);
        for (final String lang : CODEGENS.keySet()) {
            final AbstractBetterCodegen codegen = codegen(lang);
            final List<Map<String, Object>> vars =
                    enumVars(lang, List.of("paused", "active", "live"), names("x-enumnames", names), "String");
            for (final Map<String, Object> var : vars) {
                if (var.get("wire") == null) {
                    continue;
                }
                assertEquals(
                        var.get("name"),
                        codegen.enumMemberName(schema, String.valueOf(var.get("wire")), "String"),
                        lang + " " + var.get("wire"));
            }
        }
    }

    private static Map<String, Object> names(String key, Object value) {
        return ext(key, value);
    }

    @Test
    @DisplayName("enumMemberName without a names key is the derived name")
    void enumMemberNameWithoutKeysIsDerived() {
        final Schema<String> schema = new StringSchema();
        schema.setEnum(List.of("on-hold"));
        assertEquals("ON_HOLD", codegen("java").enumMemberName(schema, "on-hold", "String"));
    }

    @Test
    @DisplayName("a member named like what the enum type declares itself gets the numbered suffix")
    void reservedMemberNamesAreSuffixed() {
        assertEquals("VALUES2", enumVars("ruby", List.of("values"), ext("x-enumNames", List.of("VALUES")), "String").get(0).get("name"));
        assertEquals("value2", enumVars("dart", List.of("x"), ext("x-enumNames", List.of("value")), "String").get(0).get("name"));
        assertEquals("description2", enumVars("swift", List.of("x"), ext("x-enumNames", List.of("description")), "String").get(0).get("name"));
        assertEquals("all_values_2", enumVars("elixir", List.of("x"), ext("x-enumNames", List.of("all values")), "String").get(0).get("name"));
    }

    @Test
    @DisplayName("a derived member named like what the enum type declares itself gets the suffix too")
    void derivedReservedMemberNamesAreSuffixed() {
        assertEquals("VALUES2", enumVars("ruby", List.of("values"), new HashMap<>(), "String").get(0).get("name"));
    }

    @Test
    @DisplayName("the info summary stands in for a missing description and the contact and license reach templates")
    void infoMetadataReachesTemplates() {
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Widget API")
                        .summary("Widgets in one place")
                        .termsOfService("https://example.com/terms")
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("Widget team")
                                .email("team@example.com")
                                .url("https://example.com/help"))
                        .license(new io.swagger.v3.oas.models.info.License()
                                .name("Apache 2.0")
                                .identifier("Apache-2.0"))));
        final Map<String, Object> props = codegen.additionalProperties();
        assertAll(
                () -> assertEquals("Widgets in one place", props.get("appDescription")),
                () -> assertEquals("Widgets in one place", props.get("appSummary")),
                () -> assertEquals("Widget team", props.get("infoContactName")),
                () -> assertEquals("team@example.com", props.get("infoContactEmail")),
                () -> assertEquals("https://example.com/help", props.get("infoContactUrl")),
                () -> assertEquals("Apache 2.0", props.get("licenseName")),
                () -> assertEquals("https://example.com/terms", props.get("infoTermsOfService")),
                () -> assertEquals("Apache-2.0", props.get("licenseId")),
                () -> assertTrue(Boolean.TRUE.equals(props.get("hasInfoBlock"))));
    }

    @Test
    @DisplayName("a license identifier the parsed model dropped is read from the document")
    void licenseIdentifierFallsBackToTheDocument(@TempDir final Path dir) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, "openapi: 3.1.0\n"
                + "info:\n"
                + "  title: T\n"
                + "  license:\n"
                + "    name: Apache 2.0\n"
                + "    identifier: Apache-2.0\n");
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.setInputSpec(spec.toString());
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("T")
                        .license(new io.swagger.v3.oas.models.info.License().name("Apache 2.0"))));
        assertEquals("Apache-2.0", codegen.additionalProperties().get("licenseId"));
    }

    @Test
    @DisplayName("a document with no info metadata leaves the info block out")
    void infoBlockIsAbsentWithoutMetadata() {
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info().title("Bare").version("1")));
        assertAll(
                () -> assertFalse(Boolean.TRUE.equals(codegen.additionalProperties().get("hasInfoBlock"))),
                () -> assertFalse(codegen.additionalProperties().containsKey("infoContactName")),
                () -> assertFalse(codegen.additionalProperties().containsKey("appSummary")));
    }

    @Test
    @DisplayName("an inline response summary reaches the operation decorator as a labelled line")
    @SuppressWarnings("unchecked")
    void responseSummaryReachesOperation(@TempDir final Path dir) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, "openapi: 3.2.0\n"
                + "paths:\n"
                + "  /items:\n"
                + "    get:\n"
                + "      responses:\n"
                + "        '200':\n"
                + "          summary: Items listed\n"
                + "          description: OK\n");
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.setInputSpec(spec.toString());
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI());
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setOperationId("listItems");
        operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses()
                .addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse().description("OK")));
        final Map<String, Object> decorator = Objects.requireNonNull((Map<String, Object>) codegen
                .fromOperation("/items", "get", operation, new ArrayList<>()).vendorExtensions.get("op"));
        assertEquals(List.of("Response 200: Items listed"), decorator.get("responseSummaries"));
    }

    @Test
    @DisplayName("an operation with no response summary carries no summary lines")
    void operationWithoutSummaryHasNoLines() {
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI());
        final io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.setOperationId("listItems");
        operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        final CodegenOperation op = codegen.fromOperation("/items", "get", operation, new ArrayList<>());
        assertFalse(op.vendorExtensions.containsKey("op")
                && ((Map<?, ?>) op.vendorExtensions.get("op")).containsKey("responseSummaries"));
    }

    @Test
    @DisplayName("a server name carried from a 3.2 document reaches the server configs, and a server without one has none")
    @SuppressWarnings("unchecked")
    void serverNameReachesTheServerConfigs() {
        final AbstractBetterCodegen codegen = codegen("java");
        final io.swagger.v3.oas.models.servers.Server named =
                new io.swagger.v3.oas.models.servers.Server().url("https://a.example.com");
        named.addExtension(io.github.mridang.codegen.OpenApi32ParserExtension.SERVER_NAME, "main");
        codegen.processOpenAPI(new io.swagger.v3.oas.models.OpenAPI()
                .servers(List.of(named, new io.swagger.v3.oas.models.servers.Server().url("https://b.example.com"))));
        final List<Map<String, Object>> configs = Objects.requireNonNull(
                (List<Map<String, Object>>) codegen.additionalProperties().get("serverConfigs"));
        assertAll(
                () -> assertEquals("main", configs.get(0).get("serverName")),
                () -> assertNull(configs.get(1).get("serverName")));
    }

    @Test
    @DisplayName("a path item's summary and description reach an operation that has none, and an operation's own text wins")
    void pathItemTextReachesOperationsWithoutTheirOwn() {
        final AbstractBetterCodegen codegen = codegen("java");
        final io.swagger.v3.oas.models.Operation bare = new io.swagger.v3.oas.models.Operation();
        final io.swagger.v3.oas.models.Operation own = new io.swagger.v3.oas.models.Operation()
                .summary("Own summary").description("Own description");
        final io.swagger.v3.oas.models.OpenAPI openAPI = new io.swagger.v3.oas.models.OpenAPI()
                .path("/items", new io.swagger.v3.oas.models.PathItem()
                        .summary("Item summary").description("Item description").get(bare).post(own));
        codegen.preprocessOpenAPI(openAPI);
        assertAll(
                () -> assertEquals("Item summary", bare.getSummary()),
                () -> assertEquals("Item description", bare.getDescription()),
                () -> assertEquals("Own summary", own.getSummary()),
                () -> assertEquals("Own description", own.getDescription()));
    }

    /** Parses an inline YAML document with references resolved. */
    private static io.swagger.v3.oas.models.OpenAPI parseYaml(final String yaml) {
        final io.swagger.v3.parser.core.models.ParseOptions options =
                new io.swagger.v3.parser.core.models.ParseOptions();
        options.setResolve(true);
        return new io.swagger.parser.OpenAPIParser().readContents(yaml, null, options).getOpenAPI();
    }

    /** The form parameters of the request body of the only POST operation under /items. */
    private static Map<String, CodegenParameter> formParameters(final String content) {
        final io.swagger.v3.oas.models.OpenAPI api = parseYaml(String.join("\n",
                "openapi: 3.1.0",
                "info: {title: t, version: '1'}",
                "paths:",
                "  /items:",
                "    post:",
                "      operationId: send",
                "      requestBody:",
                "        content:",
                content,
                "      responses:",
                "        '204': {description: ok}"));
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.setOpenAPI(api);
        final io.swagger.v3.oas.models.parameters.RequestBody body =
                Objects.requireNonNull(Objects.requireNonNull(api.getPaths().get("/items")).getPost()).getRequestBody();
        return codegen.fromRequestBodyToFormParameters(body, new java.util.HashSet<>()).stream()
                .collect(Collectors.toMap(p -> p.baseName, p -> p));
    }

    @Test
    @DisplayName("the style and explode of a form field's Encoding Object become its style (D165)")
    void formEncodingStyleAndExplodeBecomeTheFieldStyle() {
        final Map<String, CodegenParameter> params = formParameters(String.join("\n",
                "          application/x-www-form-urlencoded:",
                "            schema:",
                "              type: object",
                "              properties:",
                "                piped: {type: array, items: {type: string}}",
                "                joined: {type: array, items: {type: string}}",
                "                deep: {type: object}",
                "                plain: {type: array, items: {type: string}}",
                "                reserved: {type: string}",
                "            encoding:",
                "              reserved: {allowReserved: true}",
                "              piped: {style: pipeDelimited}",
                "              joined: {explode: false}",
                "              deep: {style: deepObject}"));
        final CodegenParameter piped = Objects.requireNonNull(params.get("piped"));
        final CodegenParameter joined = Objects.requireNonNull(params.get("joined"));
        assertAll(
                () -> assertEquals("pipeDelimited", piped.style),
                () -> assertFalse(piped.isExplode),
                () -> assertEquals(true, piped.vendorExtensions.get("formStyled")),
                () -> assertEquals("form", joined.style),
                () -> assertFalse(joined.isExplode),
                () -> assertNull(Objects.requireNonNull(params.get("deep")).vendorExtensions.get("formStyled")),
                () -> assertNull(Objects.requireNonNull(params.get("plain")).vendorExtensions.get("formStyled")),
                () -> assertNull(Objects.requireNonNull(params.get("reserved")).vendorExtensions.get("formStyled")),
                () -> assertEquals(true, Objects.requireNonNull(params.get("reserved")).vendorExtensions.get("allowReserved")));
    }

    @Test
    @DisplayName("a multipart part's Encoding Object gives its Content-Type and fixed part headers (D166)")
    @SuppressWarnings("unchecked")
    void multipartEncodingGivesThePartContentTypeAndHeaders() {
        final Map<String, CodegenParameter> params = formParameters(String.join("\n",
                "          multipart/form-data:",
                "            schema:",
                "              type: object",
                "              properties:",
                "                note: {type: string}",
                "                image: {type: string, format: binary}",
                "                free: {type: string}",
                "            encoding:",
                "              note:",
                "                contentType: text/markdown",
                "                headers:",
                "                  X-Format: {schema: {type: string, default: commonmark}}",
                "                  X-Level: {schema: {type: integer, enum: [2]}}",
                "                  X-Open: {schema: {type: string}}",
                "                  Content-Type: {schema: {type: string, default: text/plain}}",
                "              image: {contentType: 'image/png, image/jpeg'}",
                "              free: {contentType: 'text/*'}"));
        final CodegenParameter note = Objects.requireNonNull(params.get("note"));
        final List<Map<String, Object>> headers =
                (List<Map<String, Object>>) note.vendorExtensions.get("partHeaders");
        assertAll(
                () -> assertEquals(true, note.vendorExtensions.get("partEncoded")),
                () -> assertEquals("text/markdown", note.vendorExtensions.get("partContentType")),
                () -> assertEquals(List.of(
                        Map.of("name", "X-Format", "value", "commonmark"),
                        Map.of("name", "X-Level", "value", "2")), headers),
                () -> assertNull(Objects.requireNonNull(params.get("image")).vendorExtensions.get("partEncoded")),
                () -> assertNull(Objects.requireNonNull(params.get("free")).vendorExtensions.get("partEncoded")));
    }

    @Test
    @DisplayName("the headers of the success responses become typed response header rows (D168)")
    @SuppressWarnings("unchecked")
    void successResponseHeadersBecomeTypedRows() {
        final io.swagger.v3.oas.models.OpenAPI api = parseYaml(String.join("\n",
                "openapi: 3.1.0",
                "info: {title: t, version: '1'}",
                "paths:",
                "  /items:",
                "    get:",
                "      operationId: readItem",
                "      responses:",
                "        '200':",
                "          description: ok",
                "          headers:",
                "            X-Count: {description: How many, schema: {type: integer, format: int32}}",
                "            X-Tags: {schema: {type: array, items: {type: string}}}",
                "            Content-Type: {schema: {type: string}}",
                "        '201':",
                "          description: made",
                "          headers:",
                "            x-count: {schema: {type: string}}",
                "            X-Done: {schema: {type: boolean}}",
                "        '404':",
                "          description: missing",
                "          headers:",
                "            X-Error: {schema: {type: string}}"));
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.setOpenAPI(api);
        final io.swagger.v3.oas.models.Operation operation =
                Objects.requireNonNull(Objects.requireNonNull(api.getPaths().get("/items")).getGet());
        final org.openapitools.codegen.CodegenOperation op =
                codegen.fromOperation("/items", "get", operation, new ArrayList<>());
        codegen.populateOperationDecorators(op);
        final Map<String, Object> deco =
                Objects.requireNonNull((Map<String, Object>) op.vendorExtensions.get("op"));
        final List<Map<String, Object>> rows =
                Objects.requireNonNull((List<Map<String, Object>>) deco.get("responseHeaders"));
        assertAll(
                () -> assertEquals(true, deco.get("hasResponseHeaders")),
                () -> assertEquals("ReadItemResponseHeaders", deco.get("responseHeadersClassName")),
                () -> assertEquals(List.of("X-Count", "X-Tags", "X-Done"),
                        rows.stream().map(r -> r.get("wireName")).collect(Collectors.toList())),
                () -> assertEquals("integer", rows.get(0).get("kind")),
                () -> assertEquals("How many", rows.get(0).get("description")),
                () -> assertEquals("string", rows.get(1).get("kind")),
                () -> assertEquals(true, rows.get(1).get("isArray")),
                () -> assertEquals("boolean", rows.get(2).get("kind")));
    }

    @Test
    @DisplayName("examples of a parameter, the request body, the responses and their headers, and the listed extensions reach the operation (D169, D170)")
    @SuppressWarnings("unchecked")
    void examplesAndListedExtensionsReachTheOperation() {
        final io.swagger.v3.oas.models.OpenAPI api = parseYaml(String.join("\n",
                "openapi: 3.1.0",
                "info: {title: t, version: '1'}",
                "paths:",
                "  /items/{id}:",
                "    put:",
                "      operationId: putItem",
                "      x-audience: internal",
                "      x-other: hidden",
                "      parameters:",
                "        - {name: id, in: path, required: true, example: i1, schema: {type: string}}",
                "      requestBody:",
                "        content:",
                "          application/json:",
                "            schema: {type: object}",
                "            examples:",
                "              small: {summary: A small one, value: {a: 1}}",
                "              stored: {summary: Kept elsewhere, externalValue: 'https://example.com/i.json'}",
                "      responses:",
                "        '200':",
                "          description: ok",
                "          headers:",
                "            X-Count: {description: How many, example: 3, schema: {type: integer}}",
                "          content:",
                "            text/plain:",
                "              schema: {type: string}",
                "              example: done"));
        final AbstractBetterCodegen codegen = codegen("java");
        codegen.additionalProperties().put("exposedExtensions", "x-audience, x-missing");
        codegen.setOpenAPI(api);
        final io.swagger.v3.oas.models.Operation operation =
                Objects.requireNonNull(Objects.requireNonNull(api.getPaths().get("/items/{id}")).getPut());
        final org.openapitools.codegen.CodegenOperation op =
                codegen.fromOperation("/items/{id}", "put", operation, new ArrayList<>());
        codegen.populateOperationDecorators(op);
        final Map<String, Object> deco =
                Objects.requireNonNull((Map<String, Object>) op.vendorExtensions.get("op"));
        final List<Map<String, Object>> examples =
                Objects.requireNonNull((List<Map<String, Object>>) deco.get("operationExamples"));
        final List<Map<String, Object>> headers =
                Objects.requireNonNull((List<Map<String, Object>>) deco.get("responseHeaders"));
        final List<Map<String, Object>> param = Objects.requireNonNull(
                (List<Map<String, Object>>) op.pathParams.get(0).vendorExtensions.get("pluralExamples"));
        assertAll(
                () -> assertEquals(List.of("Request body", "Request body", "Response 200"),
                        examples.stream().map(r -> r.get("heading")).collect(Collectors.toList())),
                () -> assertEquals("{\"a\":1}", examples.get(0).get("value")),
                () -> assertEquals("https://example.com/i.json", examples.get(1).get("value")),
                () -> assertEquals("done", examples.get(2).get("value")),
                () -> assertEquals("3", headers.get(0).get("example")),
                () -> assertEquals("i1", param.get(0).get("value")),
                () -> assertEquals(List.of(Map.of("name", "x-audience", "value", "internal")),
                        deco.get("exposedExtensions")));
    }

    @Test
    @DisplayName("a security scheme that references another becomes a copy of it, keeping its own description")
    void aSecuritySchemeReferenceBecomesACopyOfItsTarget() {
        final io.swagger.v3.oas.models.OpenAPI api = parseYaml(String.join("\n",
                "openapi: 3.1.0",
                "info: {title: t, version: '1'}",
                "paths: {}",
                "components:",
                "  securitySchemes:",
                "    Key: {type: apiKey, in: header, name: X-Key, description: The key}",
                "    Alias: {$ref: '#/components/securitySchemes/Key', description: Same key}",
                "    Chain: {$ref: '#/components/securitySchemes/Alias'}",
                "    Loop: {$ref: '#/components/securitySchemes/Loop'}"));
        AbstractBetterCodegen.inlineSecuritySchemeReferences(api);
        final Map<String, io.swagger.v3.oas.models.security.SecurityScheme> schemes =
                Objects.requireNonNull(api.getComponents().getSecuritySchemes());
        final io.swagger.v3.oas.models.security.SecurityScheme alias = Objects.requireNonNull(schemes.get("Alias"));
        final io.swagger.v3.oas.models.security.SecurityScheme chain = Objects.requireNonNull(schemes.get("Chain"));
        assertAll(
                () -> assertEquals(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY, alias.getType()),
                () -> assertEquals("X-Key", alias.getName()),
                () -> assertEquals("Same key", alias.getDescription()),
                () -> assertNull(alias.get$ref()),
                () -> assertEquals("X-Key", chain.getName()),
                () -> assertEquals("#/components/securitySchemes/Loop",
                        Objects.requireNonNull(schemes.get("Loop")).get$ref()));
    }
}
