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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.openapitools.codegen.CodegenOperation;
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
}
