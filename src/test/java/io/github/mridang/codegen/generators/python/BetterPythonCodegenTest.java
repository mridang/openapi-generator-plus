package io.github.mridang.codegen.generators.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Validates the string-literal rendering the Python authenticator wrapper uses
 * for an apiKey scheme name, so a legal header or query name never breaks the
 * generated source.
 */
class BetterPythonCodegenTest {

    @Test
    void quoteAndBackslashInAnApiKeyNameAreEscaped() {
        assertEquals("X-Api\\\"Key\\\\", BetterPythonCodegen.pythonStringBody("X-Api\"Key\\"));
    }

    @Test
    void singleQuoteDollarAndInterpolationOpenersStayLiteral() {
        assertEquals("X-Api'Key$x#{y}", BetterPythonCodegen.pythonStringBody("X-Api'Key$x#{y}"));
    }

    @Test
    void controlCharactersUseEscapeSequences() {
        assertEquals("a\\nb\\rc\\td", BetterPythonCodegen.pythonStringBody("a\nb\rc\td"));
        assertEquals("a\\x01b\\x1f", BetterPythonCodegen.pythonStringBody("a" + (char) 1 + "b" + (char) 31));
    }

    @Test
    void anOrdinaryNameIsUnchanged() {
        assertEquals("X-API-Key", BetterPythonCodegen.pythonStringBody("X-API-Key"));
    }

    /* F1055: a parameter named like an argument the operation signature adds must not duplicate it. */
    @Test
    void aParameterNamedLikeAnInjectedArgumentIsSuffixed() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        for (final String injected :
                new String[] {"options", "server", "request_content_type", "timeout", "headers", "max_retries"}) {
            assertEquals(injected + "_param", codegen.toParamName(injected));
        }
    }

    @Test
    void aCamelCasedInjectedNameIsSuffixedAfterSnakeCasing() {
        assertEquals("max_retries_param", new BetterPythonCodegen().toParamName("maxRetries"));
        assertEquals(
                "request_content_type_param",
                new BetterPythonCodegen().toParamName("requestContentType"));
    }

    @Test
    void anOrdinaryParameterNameIsNotSuffixed() {
        assertEquals("pet_id", new BetterPythonCodegen().toParamName("petId"));
    }

    /* S8: required parameters, then the server, then the options, whatever the operation secures. */
    @Test
    @ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ)
    void theServerComesBeforeTheOptionsInEverySignature() throws IOException {
        final Path api = Path.of("src/spec/resources/generated/python/petstore_client/api");
        final Pattern signature = Pattern.compile("async def \\w+\\(\\n(.*?)\\n    \\) ->", Pattern.DOTALL);
        int checked = 0;
        for (final String file : new String[] {"pet_api.py", "store_api.py"}) {
            final Matcher m = signature.matcher(Files.readString(api.resolve(file)));
            while (m.find()) {
                final String args = m.group(1);
                final int server = args.indexOf("        server:");
                final int options = args.indexOf("        options:");
                if (server >= 0 && options >= 0) {
                    assertTrue(server < options, "server must precede options in:\n" + args);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "no operation with both a server and options was found");
    }

    /* F894: the package metadata options reach the manifest and README templates. */
    @Test
    void packageMetadataOptionsAreExposedToTheTemplates() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        codegen.additionalProperties().put("repositoryUrl", "https://example.com/repo");
        codegen.additionalProperties().put("authors", "Ada Lovelace, \"Q\" Smith,");
        codegen.processOpts();

        assertEquals(true, codegen.additionalProperties().get("hasProjectUrls"));
        assertEquals(true, codegen.additionalProperties().get("hasAuthors"));
        assertEquals(
                List.of(Map.of("name", "Ada Lovelace"), Map.of("name", "\\\"Q\\\" Smith")),
                codegen.additionalProperties().get("authorsList"));
        assertEquals(null, codegen.additionalProperties().get("homepageUrl"));
    }

    @Test
    void withoutMetadataOptionsNothingIsAdvertised() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        codegen.processOpts();

        assertEquals(false, codegen.additionalProperties().get("hasProjectUrls"));
        assertEquals(false, codegen.additionalProperties().get("hasAuthors"));
    }

    /* F827: every operation gets an Options object, so the shared pass must mint it for all of them. */
    @Test
    void everyOperationGetsAnOptionsObject() {
        assertTrue(new BetterPythonCodegen().mintsOptionsForEveryOperation());
    }

    /* F862: the per-scheme authenticator reads the shared variable name, and the client shares its prefix. */
    @Test
    void aSchemeAuthenticatorReadsItsSharedEnvironmentVariable(@TempDir final Path out) throws IOException {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        codegen.setOutputDir(out.toString());
        codegen.processOpts();
        codegen.processOpenAPI(
                new OpenAPI()
                        .info(new Info().title("Acme SDK").version("1"))
                        .components(new Components()
                                .addSecuritySchemes(
                                        "tok",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer"))));

        assertEquals("ACME", codegen.additionalProperties().get("envPrefix"));
        final String source;
        try (Stream<Path> files = Files.walk(out)) {
            source = Files.readString(files.filter(f -> f.getFileName().toString().equals("tok_authenticator.py"))
                    .findFirst()
                    .orElseThrow());
        }
        assertTrue(source.contains("resolve_credential(token, 'ACME_TOKEN', 'token')")
                || source.contains("resolve_credential(token, \"ACME_TOKEN\", \"token\")"), source);
        assertTrue(source.contains("token: Optional[str] = None"), source);
        assertTrue(source.contains("token_provider: Optional[Callable[[], str]] = None"), source);
    }

    /* F857: the client names each scheme credential in snake case, escaping a keyword or a client argument. */
    @Test
    void aSchemeCredentialIsNamedAfterTheSchemeInSnakeCase() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();

        assertEquals("pet_store_bearer", codegen.schemeParamName("petStoreBearer"));
        assertEquals("api_key_header", codegen.schemeParamName("apiKeyHeader"));
        assertEquals("class_", codegen.schemeParamName("class"));
        assertEquals("authenticator_", codegen.schemeParamName("authenticator"));
        assertEquals("pool_manager_", codegen.schemeParamName("poolManager"));
        assertEquals("scheme_2fa", codegen.schemeParamName("2fa"));
        assertEquals("oauth2_read", codegen.schemeParamName("oauth2-read"));
    }

    @Test
    void theClientSchemesCarryThePythonNameAndAQuotedSchemeId() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        final Map<String, Object> shared = new java.util.LinkedHashMap<>();
        shared.put("schemeId", "we\"ird");
        shared.put("paramName", "weIrd");
        shared.put("className", "WeirdAuthenticator");

        final List<Map<String, Object>> schemes = codegen.pythonClientSchemes(List.of(shared));

        assertEquals("we_ird", schemes.get(0).get("paramName"));
        assertEquals("we\\\"ird", schemes.get(0).get("schemeLiteral"));
        assertEquals("WeirdAuthenticator", schemes.get(0).get("className"));
    }

    /* F857: the client schemes reach the templates, in the order the document declares them. */
    @Test
    void theClientSchemesAreExposedToTheTemplates(@TempDir final Path out) {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        codegen.setOutputDir(out.toString());
        codegen.processOpts();
        codegen.processOpenAPI(
                new OpenAPI()
                        .info(new Info().title("Acme SDK").version("1"))
                        .components(new Components()
                                .addSecuritySchemes(
                                        "firstToken",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer"))
                                .addSecuritySchemes(
                                        "secondKey",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name("X-Key"))));

        final Object schemes = codegen.additionalProperties().get("clientSchemes");

        assertTrue(schemes instanceof List);
        final List<?> list = java.util.Objects.requireNonNull((List<?>) schemes);
        assertEquals(2, list.size());
        assertEquals("first_token", ((Map<?, ?>) list.get(0)).get("paramName"));
        assertEquals("second_key", ((Map<?, ?>) list.get(1)).get("paramName"));
        assertEquals(true, codegen.additionalProperties().get("hasClientSchemes"));
    }

    @Test
    void twoSchemesThatReadTheSameInSnakeCaseKeepDistinctArguments() {
        final BetterPythonCodegen codegen = new BetterPythonCodegen();
        final Map<String, Object> first = new java.util.LinkedHashMap<>();
        first.put("schemeId", "api-key");
        final Map<String, Object> second = new java.util.LinkedHashMap<>();
        second.put("schemeId", "api_key");

        final List<Map<String, Object>> schemes = codegen.pythonClientSchemes(List.of(first, second));

        assertEquals("api_key", schemes.get(0).get("paramName"));
        assertEquals("api_key_", schemes.get(1).get("paramName"));
    }
}
