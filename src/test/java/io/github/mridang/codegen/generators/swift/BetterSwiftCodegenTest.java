package io.github.mridang.codegen.generators.swift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenComposedSchemas;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;

/** Unit tests for the Swift-specific operation signature ordering. */
class BetterSwiftCodegenTest {

    private static Map<String, Object> arg(String kind) {
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("isOptions", "options".equals(kind));
        m.put("isServer", "server".equals(kind));
        return m;
    }

    private static List<String> kinds(List<Map<String, Object>> args) {
        return args.stream().map(a -> (String) a.get("kind")).collect(Collectors.toList());
    }

    /* Security mints an options argument, which must come after the server. */
    @Test
    void serverComesBeforeOptions() {
        final List<Map<String, Object>> in = List.of(arg("path"), arg("options"), arg("server"));
        assertEquals(
                List.of("path", "server", "options"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(in)));
    }

    @Test
    void listsWithoutBothAreUnchanged() {
        assertEquals(
                List.of("path", "server"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(List.of(arg("path"), arg("server")))));
        assertEquals(
                List.of("path", "options"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(List.of(arg("path"), arg("options")))));
    }

    @Test
    void alreadyOrderedListIsUnchanged() {
        assertEquals(
                List.of("body", "server", "options"),
                kinds(
                        BetterSwiftCodegen.serverBeforeOptions(
                                List.of(arg("body"), arg("server"), arg("options")))));
    }

    /* An apiKey header name goes into a string literal of the generated client. */
    @Test
    void escapesQuoteAndBackslashInAnApiKeyName() {
        assertEquals("X-\\\"Key\\\"", BetterSwiftCodegen.escapeSwiftString("X-\"Key\""));
        assertEquals("a\\\\b", BetterSwiftCodegen.escapeSwiftString("a\\b"));
    }

    @Test
    void escapesControlCharactersInAnApiKeyName() {
        assertEquals("a\\u{a}b", BetterSwiftCodegen.escapeSwiftString("a\nb"));
        assertEquals("a\\u{0}b", BetterSwiftCodegen.escapeSwiftString("a\u0000b"));
    }

    @Test
    void leavesAnOrdinaryNameAlone() {
        assertEquals("X-Api-Key", BetterSwiftCodegen.escapeSwiftString("X-Api-Key"));
        assertEquals("", BetterSwiftCodegen.escapeSwiftString(null));
    }

    /* Every operation gets an options struct, so each call has a slot for per-call settings (F827). */
    @Test
    void everyOperationGetsAnOptionsStruct() {
        assertTrue(new BetterSwiftCodegen().mintsOptionsForEveryOperation());
    }

    private static AbstractBetterCodegen.SchemeAuthSpec scheme(String baseClass, List<String> params) {
        return new AbstractBetterCodegen.SchemeAuthSpec(
                "scheme", "Scheme", "", baseClass, false, params, null, null, null, null, null, null, null);
    }

    /* A credential that an environment variable can supply is optional; the host never is (F862). */
    @Test
    void credentialsThatTheEnvironmentCanSupplyAreOptional() {
        final AbstractBetterCodegen.SchemeAuthSpec basic =
                scheme("BasicAuthenticator", List.of("host", "username", "password"));
        assertEquals(
                "host: String, username: String? = nil, password: String? = nil",
                BetterSwiftCodegen.buildSwiftConstructorSignature(basic, true, true));
        assertEquals(
                "host: String, username: String, password: String",
                BetterSwiftCodegen.buildSwiftConstructorSignature(basic, false, true));
        assertEquals(
                "host: String, username: String?, password: String?",
                BetterSwiftCodegen.buildSwiftConstructorSignature(basic, true, false));
    }

    @Test
    void theForwardingCallPassesEveryParameterOn() {
        assertEquals(
                "host: host, clientId: clientId, clientSecret: clientSecret",
                BetterSwiftCodegen.buildSwiftForwardArguments(
                        scheme(
                                "OAuth2ClientCredentialsAuthenticator",
                                List.of("host", "clientId", "clientSecret"))));
    }

    /* An integer enum member follows the same lowerCamel naming as the rest of the SDK. */
    @Test
    void integerEnumMembersAreLowerCamelCase() {
        final BetterSwiftCodegen codegen = new BetterSwiftCodegen();
        assertEquals("number1", codegen.toEnumVarName("1", "Int"));
        assertEquals("numberMinus1", codegen.toEnumVarName("-1", "Int"));
        assertEquals("number3Dot5", codegen.toEnumVarName("3.5", "Double"));
    }

    /* A parameter named like an argument the generated code injects is renamed (F1055). */
    @Test
    void injectedArgumentNamesCoverEverythingTheTemplatesAdd() {
        final BetterSwiftCodegen codegen = new BetterSwiftCodegen();
        for (final String name :
                List.of(
                        "options",
                        "server",
                        "auth",
                        "requestContentType",
                        "timeout",
                        "headers",
                        "maxRetries")) {
            assertTrue(codegen.getInjectedArgumentNames().contains(name), name);
        }
    }

    private static CodegenProperty variant(String dataType) {
        final CodegenProperty property = new CodegenProperty();
        property.dataType = dataType;
        return property;
    }

    private static List<String> variantNames(String... dataTypes) {
        final CodegenModel model = new CodegenModel();
        final List<CodegenProperty> variants = new java.util.ArrayList<>();
        for (final String dataType : dataTypes) {
            variants.add(variant(dataType));
        }
        model.setComposedSchemas(new CodegenComposedSchemas(null, variants, null, null));
        BetterSwiftCodegen.nameUnionVariants(model);
        return variants.stream()
                .map(v -> (String) v.vendorExtensions.get("variantName"))
                .collect(Collectors.toList());
    }

    /* Every oneOf variant gets a name for the typed from, is and as members of its union. */
    @Test
    void unionVariantsAreNamedAfterTheirType() {
        assertEquals(List.of("Reservation", "Data", "DataList"), variantNames("Reservation", "Data", "[Data]"));
        assertEquals(List.of("StringList", "StringIntMap"), variantNames("[String]", "[String: Int]"));
    }

    @Test
    void unionVariantNamesNeverCollide() {
        assertEquals(List.of("String", "String2"), variantNames("String", "String"));
    }
}
