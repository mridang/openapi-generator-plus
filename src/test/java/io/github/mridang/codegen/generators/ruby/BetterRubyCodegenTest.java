package io.github.mridang.codegen.generators.ruby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates the literal and constant-name rendering that the Ruby templates and
 * authenticator wrappers use for spec text, so a legal header, query or enum
 * value never breaks the generated source.
 */
class BetterRubyCodegenTest {

    @Test
    void quoteInAnApiKeyNameIsEscaped() {
        assertEquals("'X-Api\\'Key'", BetterRubyCodegen.toRubyStringLiteral("X-Api'Key"));
    }

    @Test
    void backslashInAnApiKeyNameIsEscaped() {
        assertEquals("'a\\\\b'", BetterRubyCodegen.toRubyStringLiteral("a\\b"));
    }

    @Test
    void interpolationSigilsStayLiteralInSingleQuotes() {
        assertEquals("'#{x} $eq'", BetterRubyCodegen.toRubyStringLiteral("#{x} $eq"));
    }

    @Test
    void controlCharacterSwitchesToADoubleQuotedLiteralThatEscapesInterpolation() {
        assertEquals("\"a\\tb\\#{x}\"", BetterRubyCodegen.toRubyStringLiteral("a\tb#{x}"));
    }

    @Test
    void otherControlCharactersUseABracedUnicodeEscape() {
        assertEquals("\"a\\u{1}b\"", BetterRubyCodegen.toRubyStringLiteral("a\u0001b"));
    }

    @Test
    void escapingForASingleQuotedLiteralRoundTrips() {
        final String raw = "it's a\\b";
        assertEquals(raw, BetterRubyCodegen.unescapeSingleQuoted(BetterRubyCodegen.escapeSingleQuoted(raw)));
    }

    @Test
    void constantNamesAreUpperCaseIdentifiers() {
        assertEquals("A_B", BetterRubyCodegen.toRubyConstantName("a-b"));
        assertEquals("O_CLOCK", BetterRubyCodegen.toRubyConstantName("o'clock"));
    }

    @Test
    void aDigitLeadingConstantNameGetsALetterPrefix() {
        assertEquals("N2FA", BetterRubyCodegen.toRubyConstantName("2fa"));
    }

    @Test
    void aValueWithNoIdentifierCharactersTakesTheEmptyName() {
        assertEquals("EMPTY", BetterRubyCodegen.toRubyConstantName("@@"));
    }

    @Test
    void aParameterNamedLikeAnInjectedArgumentGetsASuffix() {
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        assertEquals("options_param", codegen.toParamName("options"));
        assertEquals("server_param", codegen.toParamName("server"));
        assertEquals("path_param", codegen.toParamName("path"));
        assertEquals("request_content_type_param", codegen.toParamName("requestContentType"));
    }

    @Test
    void anOrdinaryParameterNameKeepsItsName() {
        assertEquals("pet_id", new BetterRubyCodegen().toParamName("petId"));
    }

    @Test
    void aParameterNamedLikeAPerCallOptionGetsASuffix() {
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        assertEquals("timeout_param", codegen.toParamName("timeout"));
        assertEquals("headers_param", codegen.toParamName("headers"));
        assertEquals("max_retries_param", codegen.toParamName("maxRetries"));
    }

    @Test
    void manifestMetadataOptionsReachTheTemplatesAsQuoteSafeLiterals() {
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        codegen.additionalProperties().put("authors", "Ada L., Bob O'Neil");
        codegen.additionalProperties().put("repositoryUrl", "https://example.test/o'r");
        codegen.additionalProperties().put("homepageUrl", "  ");
        codegen.processOpts();

        final Map<String, Object> props = codegen.additionalProperties();
        assertEquals(
                List.of(Map.of("name", "Ada L."), Map.of("name", "Bob O\\'Neil")),
                props.get("authorList"));
        assertEquals("https://example.test/o\\'r", props.get("repositoryUrlLiteral"));
        assertFalse(props.containsKey("homepageUrl"));
        assertFalse(props.containsKey("documentationUrl"));
    }

    @Test
    void everyOperationGetsAnOptionsObject() {
        assertTrue(new BetterRubyCodegen().mintsOptionsForEveryOperation());
    }

    private static String renderScheme(SchemeAuthSpec spec, OpenAPI openApi, Path outputDir) {
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        codegen.setOutputDir(outputDir.toString());
        codegen.processOpts();
        codegen.processOpenAPI(openApi);
        return codegen.renderSchemeAuthenticator(spec);
    }

    private static OpenAPI apiWith(Map<String, SecurityScheme> schemes) {
        final OpenAPI openApi = new OpenAPI();
        openApi.setInfo(new io.swagger.v3.oas.models.info.Info().title("Acme SDK").version("1"));
        openApi.setComponents(new Components().securitySchemes(schemes));
        return openApi;
    }

    @Test
    void aBearerSchemeNamesItsOwnVariableAndTakesAProviderAfterIt(@TempDir Path outputDir) {
        final SecurityScheme bearer = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer");
        final SchemeAuthSpec spec =
                new SchemeAuthSpec(
                        "serviceToken", "ServiceToken", "", "BearerAuthenticator", false,
                        List.of("host", "token"), null, null, null, null, null, null, null);
        final String source = renderScheme(spec, apiWith(Map.of("serviceToken", bearer)), outputDir);
        assertTrue(source.contains("def initialize(host, token = nil, token_provider: nil, env: ENV)"), source);
        assertTrue(source.contains("token_env: 'ACME_TOKEN'"), source);
        assertTrue(source.contains("env: env"), source);
    }

    @Test
    void twoSchemesWithTheSameFieldReadVariablesNamedAfterTheirScheme(@TempDir Path outputDir) {
        final SecurityScheme basic = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic");
        final OpenAPI openApi = apiWith(Map.of("shopBasic", basic, "adminBasic", basic));
        final SchemeAuthSpec spec =
                new SchemeAuthSpec(
                        "adminBasic", "AdminBasic", "", "BasicAuthenticator", false,
                        List.of("host", "username", "password"), null, null, null, null, null, null, null);
        final String source = renderScheme(spec, openApi, outputDir);
        assertTrue(source.contains("def initialize(host, username = nil, password = nil, env: ENV)"), source);
        assertTrue(source.contains("username_env: 'ACME_ADMIN_BASIC_USERNAME'"), source);
        assertTrue(source.contains("password_env: 'ACME_ADMIN_BASIC_PASSWORD'"), source);
    }

    @Test
    void anApiKeySchemeNamesItsOwnVariable(@TempDir Path outputDir) {
        final SecurityScheme key =
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key");
        final SchemeAuthSpec spec =
                new SchemeAuthSpec(
                        "apiKeyHeader", "ApiKeyHeader", "", "ApiKeyAuthenticator", false,
                        List.of("host", "apiKey"), "X-Api-Key", "HEADER", null, null, null, null, null);
        final String source = renderScheme(spec, apiWith(Map.of("apiKeyHeader", key)), outputDir);
        assertTrue(source.contains("def initialize(host, api_key = nil, env: ENV)"), source);
        assertTrue(source.contains("api_key_env: 'ACME_API_KEY'"), source);
    }

    @Test
    void anOAuthSchemeResolvesEachCredentialBeforeItCallsTheBase(@TempDir Path outputDir) {
        final SecurityScheme oauth =
                new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                                new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .clientCredentials(
                                                new io.swagger.v3.oas.models.security.OAuthFlow()
                                                        .tokenUrl("https://auth.example.test/token")));
        final SchemeAuthSpec spec =
                new SchemeAuthSpec(
                        "machineAuth", "MachineAuth", "ClientCredentials",
                        "OAuth2ClientCredentialsAuthenticator", true,
                        List.of("host", "clientId", "clientSecret"), null, null,
                        "https://auth.example.test/token", null, null, null, null);
        final String source = renderScheme(spec, apiWith(Map.of("machineAuth", oauth)), outputDir);
        assertTrue(source.contains("def initialize(host, client_id = nil, client_secret = nil, env: ENV)"), source);
        assertTrue(
                source.contains("client_id = require_credential(client_id, 'ACME_CLIENT_ID', 'client_id', env)"),
                source);
        assertTrue(
                source.contains(
                        "client_secret = require_credential(client_secret, 'ACME_CLIENT_SECRET', 'client_secret', env)"),
                source);
    }

    @Test
    void theClientTakesOneKeywordCredentialPerSchemeNamedAfterIt(@TempDir Path outputDir) {
        final SecurityScheme http = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer");
        final java.util.LinkedHashMap<String, SecurityScheme> schemes = new java.util.LinkedHashMap<>();
        schemes.put("petStoreBearer", http);
        schemes.put("admin-basic", http);
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        codegen.setOutputDir(outputDir.toString());
        codegen.processOpts();
        codegen.processOpenAPI(apiWith(schemes));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> client =
                (List<Map<String, Object>>) codegen.additionalProperties().get("clientSchemes");
        assertNotNull(client);
        assertEquals(
                List.of("petStoreBearer=pet_store_bearer", "admin-basic=admin_basic"),
                client.stream()
                        .map(e -> e.get("schemeId") + "=" + e.get("argName"))
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void aCredentialNamedLikeAKeywordOrAnotherArgumentGetsASuffix() {
        final List<Map<String, Object>> schemes = new java.util.ArrayList<>();
        for (final String id : List.of("end", "authenticator", "transportOptions", "a-b", "a_b")) {
            schemes.add(new java.util.HashMap<>(Map.of("schemeId", id)));
        }

        BetterRubyCodegen.nameCredentialArguments(schemes, "end"::equals);

        assertEquals(
                List.of(
                        "end_credential",
                        "authenticator_credential",
                        "transport_options_credential",
                        "a_b",
                        "a_b_credential"),
                schemes.stream().map(e -> e.get("argName")).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void anOptionsObjectNeverCarriesAnAuthenticator(@TempDir Path outputDir) {
        final org.openapitools.codegen.CodegenOperation op = new org.openapitools.codegen.CodegenOperation();
        op.operationId = "addThing";
        op.hasAuthMethods = true;
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        codegen.setOutputDir(outputDir.toString());
        codegen.processOpts();

        final String source = codegen.generateOptionsFileContent(op, List.of(), "AddThingOptions");

        assertFalse(source.contains("auth"), source);
        assertTrue(source.contains("attr_reader :timeout, :headers, :max_retries"), source);
    }
}
