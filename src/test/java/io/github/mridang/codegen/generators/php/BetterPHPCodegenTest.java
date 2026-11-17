package io.github.mridang.codegen.generators.php;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;

/**
 * Validates the string-literal rendering that the PHP authenticator wrappers use
 * for an apiKey parameter name, token URL and scope names, so a legal header or
 * query name never breaks the generated source.
 */
class BetterPHPCodegenTest {

    @Test
    void quoteInNameIsEscaped() {
        assertEquals("'X-Api\\'Key'", BetterPHPCodegen.toPhpStringLiteral("X-Api'Key"));
    }

    @Test
    void backslashInNameIsEscaped() {
        assertEquals("'a\\\\b'", BetterPHPCodegen.toPhpStringLiteral("a\\b"));
    }

    @Test
    void dollarSignStaysLiteralInSingleQuotes() {
        assertEquals("'$apiKey'", BetterPHPCodegen.toPhpStringLiteral("$apiKey"));
    }

    @Test
    void controlCharacterUsesDoubleQuotedEscape() {
        assertEquals("\"a\\tb\\$c\"", BetterPHPCodegen.toPhpStringLiteral("a\tb$c"));
    }

    @Test
    void placesTheServerBeforeAnOptionalOptionsObject() {
        final Map<String, Object> path = Map.of("paramName", "petId");
        final Map<String, Object> options =
                Map.of("paramName", "options", "isOptions", true, "nullable", true);
        final Map<String, Object> server = Map.of("paramName", "server", "isServer", true);

        assertEquals(
                List.of(path, server, options),
                BetterPHPCodegen.serverBeforeOptions(List.of(path, options, server)));
    }

    @Test
    void keepsARequiredOptionsObjectBeforeTheServer() {
        final Map<String, Object> path = Map.of("paramName", "petId");
        final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);
        final Map<String, Object> server = Map.of("paramName", "server", "isServer", true);

        assertEquals(
                List.of(path, options, server),
                BetterPHPCodegen.serverBeforeOptions(List.of(path, options, server)));
    }

    @Test
    void keepsASignatureWithoutAServerUnchanged() {
        final Map<String, Object> path = Map.of("paramName", "petId");
        final Map<String, Object> options =
                Map.of("paramName", "options", "isOptions", true, "nullable", true);

        assertEquals(
                List.of(path, options), BetterPHPCodegen.serverBeforeOptions(List.of(path, options)));
    }

    @Test
    void prefixesAModelNamedLikeAForbiddenClassName() {
        final BetterPHPCodegen codegen = new BetterPHPCodegen();

        assertEquals("ModelString", codegen.toModelName("String"));
        assertEquals("ModelObject", codegen.toModelName("Object"));
        assertEquals("ModelInt", codegen.toModelName("Int"));
        assertEquals("ModelMixed", codegen.toModelName("Mixed"));
    }

    @Test
    void keepsAModelNamedLikeAnSdkOrBuiltInTypeThatPhpAllows() {
        final BetterPHPCodegen codegen = new BetterPHPCodegen();

        assertEquals("Error", codegen.toModelName("Error"));
        assertEquals("Result", codegen.toModelName("Result"));
        assertEquals("Pet", codegen.toModelName("Pet"));
    }

    @Test
    void suffixesAParameterNamedLikeAnInjectedArgument() {
        final BetterPHPCodegen codegen = new BetterPHPCodegen();

        assertEquals("optionsParam", codegen.toParamName("options"));
        assertEquals("serverParam", codegen.toParamName("server"));
        assertEquals("requestContentTypeParam", codegen.toParamName("requestContentType"));
        assertEquals("petId", codegen.toParamName("pet_id"));
    }

    @Test
    void rendersTheNamedExamplesOfAParameterAsDocblockLines() {
        final CodegenParameter parameter = new CodegenParameter();
        parameter.vendorExtensions = new HashMap<>();
        parameter.vendorExtensions.put(
                "pluralExamples",
                List.of(
                        Map.of("value", "available", "summary", "Only free items"),
                        Map.of("value", "a*/b", "summary", "Bad */ text")));

        assertEquals(
                List.of("available Only free items", "a*_/b Bad *_/ text"),
                BetterPHPCodegen.exampleDocLines(parameter));
    }

    @Test
    void rendersNoExampleLinesForAParameterWithoutExamples() {
        final CodegenParameter parameter = new CodegenParameter();

        assertEquals(List.of(), BetterPHPCodegen.exampleDocLines(parameter));
    }

    @Test
    void rendersTheOasExamplesMapOfAnOptionalParameterBeforeTheSharedPassRuns() {
        final CodegenParameter parameter = new CodegenParameter();
        parameter.examples = new java.util.LinkedHashMap<>();
        final io.swagger.v3.oas.models.examples.Example example =
                new io.swagger.v3.oas.models.examples.Example();
        example.setSummary("Sold pets");
        example.setValue("sold");
        parameter.examples.put("sold_pets", example);

        assertEquals(List.of("sold Sold pets"), BetterPHPCodegen.exampleDocLines(parameter));
    }

    @Test
    void mintsAnOptionsObjectForEveryOperation() {
        assertTrue(new BetterPHPCodegen().mintsOptionsForEveryOperation());
    }

    @Test
    void readsOnlyTheCredentialsOfBasicBearerAndApiKeySchemesFromTheEnvironment() {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(
                "envVars",
                List.of(
                        envVar("host", "ACME_HOST"),
                        envVar("token", "ACME_TOKEN"),
                        envVar("clientId", "ACME_CLIENT_ID")));

        assertEquals(
                List.of(envVar("token", "ACME_TOKEN")),
                BetterPHPCodegen.schemeCredentialEnvVars(scheme("BearerAuthenticator"), ctx));
        assertEquals(
                List.of(),
                BetterPHPCodegen.schemeCredentialEnvVars(
                        scheme("OAuth2ClientCredentialsAuthenticator"), ctx));
    }

    @Test
    void readsTheUsernameAndPasswordOfABasicSchemeFromTheEnvironment() {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(
                "envVars",
                List.of(envVar("username", "ACME_USERNAME"), envVar("password", "ACME_PASSWORD")));

        assertEquals(
                List.of(envVar("username", "ACME_USERNAME"), envVar("password", "ACME_PASSWORD")),
                BetterPHPCodegen.schemeCredentialEnvVars(scheme("BasicAuthenticator"), ctx));
    }

    @Test
    void startsAReferencedEnumPropertyAtTheDefaultNextToTheReference() {
        final CodegenProperty property = siblingDefault("on-hold", true);
        property.dataType = "\\Acme\\Models\\Availability";
        property.vendorExtensions.put("siblingDefaultEnumVar", "ON_HOLD");

        assertEquals("\\Acme\\Models\\Availability::ON_HOLD", BetterPHPCodegen.siblingDefaultLiteral(property));
    }

    @Test
    void startsAReferencedScalarPropertyAtTheDefaultNextToTheReference() {
        final CodegenProperty text = siblingDefault("it's", false);
        text.dataType = "string";
        final CodegenProperty count = siblingDefault("3", false);
        count.dataType = "int";
        final CodegenProperty ratio = siblingDefault("0.5", false);
        ratio.dataType = "float";
        final CodegenProperty flag = siblingDefault("TRUE", false);
        flag.dataType = "bool";

        assertEquals("'it\\'s'", BetterPHPCodegen.siblingDefaultLiteral(text));
        assertEquals("3", BetterPHPCodegen.siblingDefaultLiteral(count));
        assertEquals("0.5", BetterPHPCodegen.siblingDefaultLiteral(ratio));
        assertEquals("true", BetterPHPCodegen.siblingDefaultLiteral(flag));
    }

    @Test
    void leavesAPropertyWithoutAConstantFormOfItsSiblingDefaultAlone() {
        final CodegenProperty model = siblingDefault("{}", false);
        model.dataType = "\\Acme\\Models\\Address";
        final CodegenProperty count = siblingDefault("many", false);
        count.dataType = "int";
        final CodegenProperty plain = new CodegenProperty();
        plain.vendorExtensions = new HashMap<>();
        plain.dataType = "string";

        assertNull(BetterPHPCodegen.siblingDefaultLiteral(model));
        assertNull(BetterPHPCodegen.siblingDefaultLiteral(count));
        assertNull(BetterPHPCodegen.siblingDefaultLiteral(plain));
    }

    private static Map<String, Object> envVar(String field, String envName) {
        final Map<String, Object> entry = new HashMap<>();
        entry.put("field", field);
        entry.put("envName", envName);
        return entry;
    }

    private static AbstractBetterCodegen.SchemeAuthSpec scheme(String baseClass) {
        return new AbstractBetterCodegen.SchemeAuthSpec(
                "scheme", "Scheme", "", baseClass, false, List.of("host"),
                null, null, null, null, null, null, null);
    }

    private static CodegenProperty siblingDefault(String wire, boolean isEnum) {
        final CodegenProperty property = new CodegenProperty();
        property.vendorExtensions = new HashMap<>();
        property.vendorExtensions.put("siblingDefault", wire);
        property.vendorExtensions.put("siblingDefaultIsEnum", isEnum);
        return property;
    }

    @Test
    void writesThePackageMetadataOptionsIntoTheComposerManifest() {
        final Map<String, Object> options = new HashMap<>();
        options.put("homepageUrl", "https://example.com");
        options.put("repositoryUrl", "https://github.com/acme/sdk");
        options.put("documentationUrl", "https://docs.example.com");
        options.put("issueTrackerUrl", "https://github.com/acme/sdk/issues");
        options.put("authors", "Ada Lovelace <ada@example.com>; Bob \"B\" Smith");

        assertEquals(
                "    \"homepage\": \"https://example.com\",\n"
                        + "    \"authors\": [{\"name\": \"Ada Lovelace\", \"email\": \"ada@example.com\"},"
                        + " {\"name\": \"Bob \\\"B\\\" Smith\"}],\n"
                        + "    \"support\": {\"source\": \"https://github.com/acme/sdk\","
                        + " \"docs\": \"https://docs.example.com\","
                        + " \"issues\": \"https://github.com/acme/sdk/issues\"},\n",
                BetterPHPCodegen.composerExtras(options));
    }

    @Test
    void writesNoPackageMetadataWhenNoOptionIsSet() {
        assertEquals("", BetterPHPCodegen.composerExtras(new HashMap<>()));
    }

    @Test
    void readsTheAuthorsOptionAsAListOrAString() {
        assertEquals(List.of("A", "B"), BetterPHPCodegen.authorList("A, B,"));
        assertEquals(List.of("A", "B"), BetterPHPCodegen.authorList(List.of(" A ", "B", " ")));
        assertEquals(List.of(), BetterPHPCodegen.authorList(null));
    }
}
