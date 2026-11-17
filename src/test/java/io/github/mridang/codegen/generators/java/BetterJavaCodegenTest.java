package io.github.mridang.codegen.generators.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.CodegenComposedSchemas;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BetterJavaCodegen}: the generator identity, naming conventions and
 * collection type templates, plus Bucket 4.8 ({@code format:time} -> {@code LocalTime},
 * {@code format:duration} -> {@code Duration}) and Bucket 3.1 (API-key header
 * harvesting) in {@link BetterJavaCodegen}. Both behaviours are pure codegen
 * settings — no generation roundtrip is needed.
 */
class BetterJavaCodegenTest {

    /* Bucket 4.8 — typeMapping + importMapping for java.time.LocalTime. */
    @Test
    void timeFormatMapsToLocalTime() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("LocalTime", codegen.typeMapping().get("time"));
        assertEquals("java.time.LocalTime", codegen.importMapping().get("LocalTime"));
    }

    /* Only Error clashes with java.lang; Result keeps its own name. */
    @Test
    void onlyErrorIsPrefixedAsModelName() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("Result", codegen.toModelName("Result"));
        assertEquals("ModelError", codegen.toModelName("Error"));
    }

    /* An apiKey header name goes into a Java string literal, so quotes and backslashes are escaped. */
    @Test
    void apiKeyHeaderNamesAreEscapedForJavaLiterals() {
        assertEquals("X-Api\\\"Key\\\\", BetterJavaCodegen.escapeJavaLiteral("X-Api\"Key\\"));
        assertEquals("X-Api'Key$", BetterJavaCodegen.escapeJavaLiteral("X-Api'Key$"));
    }

    /* A schema named like a java.lang type is prefixed so it cannot shadow it (F173). */
    @Test
    void javaLangNamedModelsArePrefixed() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("ModelString", codegen.toModelName("String"));
        assertEquals("ModelObject", codegen.toModelName("Object"));
        assertEquals("ModelException", codegen.toModelName("Exception"));
        assertEquals("Pet", codegen.toModelName("Pet"));
    }

    /* Bucket 4.8 — typeMapping + importMapping for java.time.Duration. */
    @Test
    void durationFormatMapsToDuration() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("Duration", codegen.typeMapping().get("duration"));
        assertEquals("java.time.Duration", codegen.importMapping().get("Duration"));
    }

    /*
     * Bucket 3.1 — apiKey-in-header security schemes are harvested at
     * processOpenAPI time and exposed as `apiKeyHeaderNames`. The triple
     * Authorization/Cookie/Proxy-Authorization is always present at runtime
     * via the static initializer in DefaultApiClient.java; this codegen-side
     * test asserts that custom names are surfaced for the template to merge.
     */
    @Test
    void apiKeyInHeaderSchemesAreHarvested() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();

        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        SecurityScheme headerKey = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Api-Key");
        SecurityScheme queryKey = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.QUERY)
                .name("api_key");
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer");
        components.addSecuritySchemes("headerKey", headerKey);
        components.addSecuritySchemes("queryKey", queryKey);
        components.addSecuritySchemes("bearer", bearer);
        openAPI.setComponents(components);

        codegen.processOpenAPI(openAPI);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> harvested =
                (List<Map<String, String>>) codegen.additionalProperties().get("apiKeyHeaderNames");
        assertNotNull(harvested);
        assertEquals(1, harvested.size(),
                "only apiKey schemes with in=header must be harvested");
        assertEquals("X-Api-Key", harvested.get(0).get("name"));
        assertEquals("x-api-key", harvested.get(0).get("lowerName"),
                "lowercased mirror must be emitted for the runtime sensitive-header set");
        Boolean flag = (Boolean) codegen.additionalProperties().get("hasApiKeyHeaderNames");
        assertNotNull(flag);
        assertTrue(flag);
    }

    @Test
    void apiKeyHarvestDeduplicatesByLowercase() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        components.addSecuritySchemes("k1", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Token"));
        components.addSecuritySchemes("k2", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("x-token"));
        openAPI.setComponents(components);

        codegen.processOpenAPI(openAPI);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> harvested =
                (List<Map<String, String>>) codegen.additionalProperties().get("apiKeyHeaderNames");
        assertNotNull(harvested);
        assertEquals(1, harvested.size(), "duplicate (case-insensitive) names must dedupe");
    }

    /* An apiKey scheme name holding quotes or backslashes must stay one literal. */
    @Test
    void apiKeyNameIsEscapedForJavaLiteral() {
        assertEquals("X-Api\\\"Key\\\\", BetterJavaCodegen.escapeJavaLiteral("X-Api\"Key\\"));
        assertEquals("X-Api'Key$", BetterJavaCodegen.escapeJavaLiteral("X-Api'Key$"));
    }

    /* F803 — the server argument comes before the options object. */
    @Test
    void serverArgumentPrecedesTheOptionsObject() {
        Map<String, Object> path = Map.of("kind", "path", "paramName", "petId");
        Map<String, Object> options = Map.of("kind", "options", "isOptions", true, "paramName", "options");
        Map<String, Object> server = Map.of("kind", "server", "isServer", true, "paramName", "server");
        List<Map<String, Object>> reordered =
                BetterJavaCodegen.serverBeforeOptions(List.of(path, options, server));
        assertEquals(List.of(path, server, options), reordered);
        assertEquals(List.of(path, options), BetterJavaCodegen.serverBeforeOptions(List.of(path, options)));
        assertEquals(List.of(path, server), BetterJavaCodegen.serverBeforeOptions(List.of(path, server)));
    }

    @Test
    void generatorIdentityIsJavaPlus() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("java-plus", codegen.getName());
        assertEquals(org.openapitools.codegen.GeneratorLanguage.JAVA, codegen.generatorLanguage());
    }

    @Test
    void sourceFolderDefaultsToMavenMainJava() {
        assertEquals(java.nio.file.Path.of("src", "main", "java").toString(),
                new BetterJavaCodegen().getSourceFolder());
    }

    @Test
    void variablesAndOperationsAreCamelCaseAndEnumsUpperSnakeCase() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals(io.github.mridang.codegen.generators.NamingConvention.CAMEL_CASE,
                codegen.getVarCasing());
        assertEquals(io.github.mridang.codegen.generators.NamingConvention.CAMEL_CASE,
                codegen.getOperationIdCasing());
        assertEquals(io.github.mridang.codegen.generators.NamingConvention.UPPER_SNAKE_CASE,
                codegen.getEnumCasing());
    }

    @Test
    void collectionTypeTemplatesProduceGenericJavaTypes() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("List<String>", String.format(codegen.getArrayTypeTemplate(), "List", "String"));
        assertEquals("Map<String, Integer>",
                String.format(codegen.getMapTypeTemplate(), "Map", "String", "Integer"));
        assertEquals("LinkedHashSet<", codegen.getUniqueItemsSetType());
    }

    @Test
    void discriminatorHandlingIsEnabledForJava() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertTrue(codegen.setsDiscriminatorParent());
        assertTrue(codegen.setsDiscriminatorDefaultOnChildren());
        assertTrue(codegen.demotesDiscriminatorFromRequiredVars());
    }

    @Test
    void renamesAParameterNamedLikeAnInjectedArgument() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("optionsParam", codegen.toParamName("options"));
        assertEquals("serverParam", codegen.toParamName("server"));
        assertEquals("requestContentTypeParam", codegen.toParamName("requestContentType"));
    }

    @Test
    void renamesAParameterNamedLikeAPerCallSetting() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        assertEquals("authParam", codegen.toParamName("auth"));
        assertEquals("timeoutParam", codegen.toParamName("timeout"));
        assertEquals("maxRetriesParam", codegen.toParamName("maxRetries"));
        assertEquals("headersParam", codegen.toParamName("headers"));
    }

    @Test
    void recordsHowACollectionPropertyIsCopiedIntoTheImmutableModel() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        String[][] rows = {
            {"List<String>", "List", "ArrayList"},
            {"Set<String>", "Set", "LinkedHashSet"},
            {"Map<String, Integer>", "Map", "LinkedHashMap"},
        };
        for (String[] row : rows) {
            CodegenProperty property = new CodegenProperty();
            property.dataType = row[0];
            codegen.postProcessModelProperty(new CodegenModel(), property);
            assertEquals(row[1], property.vendorExtensions.get("copyKind"), row[0]);
            assertEquals(row[2], property.vendorExtensions.get("copyImpl"), row[0]);
        }
        CodegenProperty scalar = new CodegenProperty();
        scalar.dataType = "String";
        codegen.postProcessModelProperty(new CodegenModel(), scalar);
        assertTrue(scalar.vendorExtensions.get("copyKind") == null);
    }

    @Test
    void namesTheVariantsOfAUnionForTheTypedFactories() {
        CodegenModel model = new CodegenModel();
        List<CodegenProperty> variants = new java.util.ArrayList<>();
        for (String type : new String[] {"Reservation", "List<String>", "byte[]", "Map<String, Object>"}) {
            CodegenProperty variant = new CodegenProperty();
            variant.dataType = type;
            variants.add(variant);
        }
        model.setComposedSchemas(
                new CodegenComposedSchemas(List.of(), variants, List.of(), null));

        BetterJavaCodegen.nameUnionVariants(model);

        assertEquals("Reservation", variants.get(0).vendorExtensions.get("variantName"));
        assertEquals("ListOfString", variants.get(1).vendorExtensions.get("variantName"));
        assertEquals("ByteArray", variants.get(2).vendorExtensions.get("variantName"));
        assertEquals("MapOfStringObject", variants.get(3).vendorExtensions.get("variantName"));
        assertEquals(true, variants.get(0).vendorExtensions.get("isPlainVariant"));
        assertEquals(false, variants.get(1).vendorExtensions.get("isPlainVariant"));
        assertEquals(false, variants.get(2).vendorExtensions.get("isPlainVariant"));
    }

    @Test
    void onlyAnOperationWithNothingButAnOptionalBodyGetsTheNoArgumentOverload() {
        CodegenOperation op = new CodegenOperation();
        op.bodyParam = new CodegenParameter();
        op.bodyParam.required = false;
        assertTrue(BetterJavaCodegen.hasOptionalBodyOnly(op));

        op.bodyParam.required = true;
        assertTrue(!BetterJavaCodegen.hasOptionalBodyOnly(op));

        op.bodyParam.required = false;
        op.pathParams.add(new CodegenParameter());
        assertTrue(!BetterJavaCodegen.hasOptionalBodyOnly(op));

        op = new CodegenOperation();
        op.bodyParam = new CodegenParameter();
        op.hasAuthMethods = true;
        assertTrue(!BetterJavaCodegen.hasOptionalBodyOnly(op));
    }

    @Test
    void homepageDefaultsToTheRepositoryAndAuthorsBecomeAList() {
        BetterJavaCodegen codegen = new BetterJavaCodegen();
        codegen.additionalProperties().put("repositoryUrl", "https://example.com/acme/client");
        codegen.additionalProperties().put("authors", "Ada Lovelace, Grace Hopper");

        codegen.processOpts();

        assertEquals("https://example.com/acme/client", codegen.additionalProperties().get("homepageUrl"));
        assertEquals(
                List.of(Map.of("name", "Ada Lovelace"), Map.of("name", "Grace Hopper")),
                codegen.additionalProperties().get("authorList"));
    }

    /* F827 - every operation gets an Options object so each call has a per-call settings slot. */
    @Test
    void everyOperationGetsAnOptionsObject() {
        assertTrue(new BetterJavaCodegen().mintsOptionsForEveryOperation());
    }

    /** Reaches the protected scheme rendering of the generator. */
    private static final class SchemeRenderer extends BetterJavaCodegen {
        String render(String schemeId, SecurityScheme scheme) {
            return renderSchemeAuthenticator(
                    java.util.Objects.requireNonNull(buildSchemeAuthSpec(schemeId, scheme)));
        }
    }

    private static String renderedScheme(String schemeId, SecurityScheme scheme) {
        SchemeRenderer codegen = new SchemeRenderer();
        /* processOpenAPI writes the authenticator files, so keep them out of the repository. */
        codegen.setOutputDir(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "scheme-render-" + schemeId)
                        .toString());
        codegen.additionalProperties().put("envVarPrefix", "Acme SDK");
        codegen.processOpts();
        codegen.processOpenAPI(
                new OpenAPI()
                        .info(new io.swagger.v3.oas.models.info.Info().title("Acme").version("1"))
                        .components(new Components().addSecuritySchemes(schemeId, scheme)));
        return codegen.render(schemeId, scheme);
    }

    /* F862 - a credential passed as null or empty is read from the scheme's variable. */
    @Test
    void apiKeyWrapperResolvesItsKeyFromTheEnvironmentVariable() {
        String source =
                renderedScheme(
                        "headerKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key"));

        assertTrue(source.contains("@Nullable String apiKey"), source);
        assertTrue(
                source.contains("resolveCredential(apiKey, \"apiKey\", \"ACME_SDK_API_KEY\", environment)"),
                source);
        assertTrue(source.contains("this(host, apiKey, System.getenv())"), source);
        assertTrue(!source.contains("Supplier"), source);
    }

    /* F862 - a bearer wrapper resolves its token like any other credential. */
    @Test
    void bearerWrapperResolvesItsTokenFromTheEnvironmentVariable() {
        String source =
                renderedScheme(
                        "bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));

        assertTrue(
                source.contains("resolveCredential(token, \"token\", \"ACME_SDK_TOKEN\", environment)"),
                source);
        assertTrue(source.contains("@Nullable String token"), source);
        assertTrue(!source.contains("Supplier"), source);
    }

    /* F862 - the OAuth wrappers resolve every credential before the base constructor runs. */
    @Test
    void oauthWrapperResolvesEveryCredentialBeforeTheSuperConstructor() {
        io.swagger.v3.oas.models.security.OAuthFlow flow =
                new io.swagger.v3.oas.models.security.OAuthFlow().tokenUrl("https://auth.example.com/t");
        String source =
                renderedScheme(
                        "machine",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows().clientCredentials(flow)));

        assertTrue(source.contains("\"ACME_SDK_CLIENT_ID\""), source);
        assertTrue(source.contains("\"ACME_SDK_CLIENT_SECRET\""), source);
        assertTrue(source.contains("resolveCredential(clientId,"), source);
        assertTrue(source.contains("resolveCredential(clientSecret,"), source);
    }
}
