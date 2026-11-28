package io.github.mridang.codegen.generators.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenComposedSchemas;

/** Unit tests of the naming rules in {@link BetterKotlinCodegen}. */
class BetterKotlinCodegenTest {

    /* A schema named like a standard-library type would shadow it in the models package. */
    @Test
    void modelsNamedLikeStandardLibraryTypesGetThePrefix() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("ModelError", codegen.toModelName("Error"));
        assertEquals("ModelResult", codegen.toModelName("Result"));
        assertEquals("ModelList", codegen.toModelName("List"));
        assertEquals("ModelException", codegen.toModelName("Exception"));
    }

    @Test
    void modelsWithOrdinaryNamesKeepTheirName() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("Category", codegen.toModelName("Category"));
        assertEquals("ErrorDetail", codegen.toModelName("ErrorDetail"));
    }

    /* A path or body parameter named like an argument the operations inject is suffixed. */
    @Test
    void parametersNamedLikeInjectedArgumentsGetASuffix() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("optionsParam", codegen.toParamName("options"));
        assertEquals("serverParam", codegen.toParamName("server"));
        assertEquals("requestContentTypeParam", codegen.toParamName("requestContentType"));
        assertEquals("authParam", codegen.toParamName("auth"));
        assertEquals("timeoutParam", codegen.toParamName("timeout"));
        assertEquals("headersParam", codegen.toParamName("headers"));
        assertEquals("maxRetriesParam", codegen.toParamName("max_retries"));
        assertEquals("pathParam", codegen.toParamName("path"));
        assertEquals("urlParam", codegen.toParamName("url"));
    }

    @Test
    void otherParametersKeepTheirName() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("itemId", codegen.toParamName("item_id"));
        assertEquals("status", codegen.toParamName("status"));
    }

    private static CodegenProperty property(String dataType, boolean required, boolean nullable) {
        CodegenProperty prop = new CodegenProperty();
        prop.dataType = dataType;
        prop.required = required;
        prop.isNullable = nullable;
        return prop;
    }

    /* BigDecimal.equals depends on the scale, so a container of decimals compares by numeric value too (F455). */
    @Test
    void containersOfDecimalsAreComparedByNumericValue() {
        CodegenProperty list = property("List<BigDecimal>", false, false);
        assertTrue(BetterKotlinCodegen.markEquality(list));
        assertEquals(true, list.vendorExtensions.get("eqDecimalList"));
        assertEquals(true, list.vendorExtensions.get("eqDecimalContainer"));
        assertEquals(true, list.vendorExtensions.get("eqDecimalNullable"));

        CodegenProperty set = property("LinkedHashSet<BigDecimal>", true, false);
        assertTrue(BetterKotlinCodegen.markEquality(set));
        assertEquals(true, set.vendorExtensions.get("eqDecimalSet"));
        assertEquals(false, set.vendorExtensions.get("eqDecimalNullable"));

        CodegenProperty map = property("Map<String, BigDecimal>", true, true);
        assertTrue(BetterKotlinCodegen.markEquality(map));
        assertEquals(true, map.vendorExtensions.get("eqDecimalMap"));
        assertEquals(true, map.vendorExtensions.get("eqDecimalNullable"));
    }

    @Test
    void otherContainersKeepTheDefaultEquality() {
        assertFalse(BetterKotlinCodegen.markEquality(property("List<String>", true, false)));
        assertFalse(BetterKotlinCodegen.markEquality(property("Map<String, Long>", true, false)));
        CodegenProperty plain = property("List<Int>", true, false);
        BetterKotlinCodegen.markEquality(plain);
        assertEquals(false, plain.vendorExtensions.get("eqDecimalContainer"));
    }

    /* A nullable java.time field is compared with a null check and equals(), because Kotlin 2.2
     * warns that == on a nullable value-based type is identity-sensitive (#124). */
    @Test
    void nullableTimeFieldsAreComparedWithoutAnIdentitySensitiveOperation() {
        CodegenProperty optional = property("OffsetDateTime", false, false);
        BetterKotlinCodegen.markEquality(optional);
        assertEquals(true, optional.vendorExtensions.get("eqValueBased"));

        CodegenProperty qualified = property("java.time.Duration", true, true);
        BetterKotlinCodegen.markEquality(qualified);
        assertEquals(true, qualified.vendorExtensions.get("eqValueBased"));

        CodegenProperty required = property("LocalDate", true, false);
        BetterKotlinCodegen.markEquality(required);
        assertEquals(false, required.vendorExtensions.get("eqValueBased"));

        CodegenProperty text = property("String", false, false);
        BetterKotlinCodegen.markEquality(text);
        assertEquals(false, text.vendorExtensions.get("eqValueBased"));
    }

    /* The package link options reach the POM and the README (F894). */
    @Test
    void homepageDefaultsToTheRepositoryAndAuthorsAreSplit() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        codegen.additionalProperties().put("repositoryUrl", "https://example.com/org/repo");
        codegen.additionalProperties().put("authors", "Ada Lovelace, Alan Turing,, ");
        codegen.additionalProperties().put("documentationUrl", "  ");
        codegen.resolvePackageLinks();

        assertEquals("https://example.com/org/repo", codegen.additionalProperties().get("homepageUrl"));
        assertEquals(
                java.util.List.of(
                        java.util.Map.of("name", "Ada Lovelace"), java.util.Map.of("name", "Alan Turing")),
                codegen.additionalProperties().get("authorList"));
        assertEquals(true, codegen.additionalProperties().get("hasAuthors"));
        assertFalse(codegen.additionalProperties().containsKey("documentationUrl"));
    }

    @Test
    void noLinkOptionsMeansNoLinks() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        codegen.resolvePackageLinks();

        assertFalse(codegen.additionalProperties().containsKey("homepageUrl"));
        assertEquals(false, codegen.additionalProperties().get("hasAuthors"));
    }

    @Test
    void anExplicitHomepageIsKept() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        codegen.additionalProperties().put("repositoryUrl", "https://example.com/org/repo");
        codegen.additionalProperties().put("homepageUrl", "https://example.com");
        codegen.resolvePackageLinks();

        assertEquals("https://example.com", codegen.additionalProperties().get("homepageUrl"));
    }

    @Test
    void theFormatterDownloadsKtlintToAFileNamedForItsOwnProcess() {
        String commands = String.join("\n", new BetterKotlinCodegen().getFormatterCommands());

        assertTrue(commands.contains("/cache/ktlint-1.5.0.part.$$"));
        assertFalse(commands.contains("/cache/ktlint-1.5.0.part "));
    }

    /* F827: every operation takes an Options object, even one with no parameter and no security. */
    @Test
    void everyOperationGetsAnOptionsObject() {
        assertTrue(new BetterKotlinCodegen().mintsOptionsForEveryOperation());
    }

    private static CodegenModel unionOf(String... types) {
        CodegenModel model = new CodegenModel();
        List<CodegenProperty> variants = new ArrayList<>();
        for (String type : types) {
            CodegenProperty variant = new CodegenProperty();
            variant.dataType = type;
            variants.add(variant);
        }
        model.setComposedSchemas(new CodegenComposedSchemas(List.of(), variants, List.of(), null));
        return model;
    }

    /* F878: a union names its variants for the typed factories and accessors. */
    @Test
    void namesTheVariantsOfAUnion() {
        CodegenModel model = unionOf("Reservation", "List<String>", "ByteArray", "Map<String, Any>");

        BetterKotlinCodegen.nameUnionVariants(model);

        List<CodegenProperty> variants = model.getComposedSchemas().getOneOf();
        assertEquals("Reservation", variants.get(0).vendorExtensions.get("variantName"));
        assertEquals("ListOfString", variants.get(1).vendorExtensions.get("variantName"));
        assertEquals("ByteArray", variants.get(2).vendorExtensions.get("variantName"));
        assertEquals("MapOfStringAny", variants.get(3).vendorExtensions.get("variantName"));
    }

    /* An erased generic type cannot be tested with `is`, and `is Any` is always true. */
    @Test
    void onlyABareClassNameVariantGetsAnIsAndAsAccessor() {
        CodegenModel model = unionOf("Reservation", "ByteArray", "List<String>", "Any");

        BetterKotlinCodegen.nameUnionVariants(model);

        List<CodegenProperty> variants = model.getComposedSchemas().getOneOf();
        assertEquals(true, variants.get(0).vendorExtensions.get("isPlainVariant"));
        assertEquals(true, variants.get(1).vendorExtensions.get("isPlainVariant"));
        assertEquals(false, variants.get(2).vendorExtensions.get("isPlainVariant"));
        assertEquals(false, variants.get(3).vendorExtensions.get("isPlainVariant"));
    }

    @Test
    void variantsThatWouldShareANameAreNumbered() {
        CodegenModel model = unionOf("String", "kotlin.String", "Int");

        BetterKotlinCodegen.nameUnionVariants(model);

        List<CodegenProperty> variants = model.getComposedSchemas().getOneOf();
        assertEquals("String", variants.get(0).vendorExtensions.get("variantName"));
        assertEquals("String2", variants.get(1).vendorExtensions.get("variantName"));
        assertEquals("Int", variants.get(2).vendorExtensions.get("variantName"));
    }

    /* F878: an anyOf wrapper has the same typed accessors as a oneOf wrapper. */
    @Test
    void namesTheVariantsOfAnAnyOfUnion() {
        CodegenModel model = new CodegenModel();
        CodegenProperty first = new CodegenProperty();
        first.dataType = "Medication";
        CodegenProperty second = new CodegenProperty();
        second.dataType = "List<String>";
        model.setComposedSchemas(
                new CodegenComposedSchemas(List.of(), List.of(), List.of(first, second), null));

        BetterKotlinCodegen.nameUnionVariants(model);

        assertEquals("Medication", first.vendorExtensions.get("variantName"));
        assertEquals(true, first.vendorExtensions.get("isPlainVariant"));
        assertEquals("ListOfString", second.vendorExtensions.get("variantName"));
        assertEquals(false, second.vendorExtensions.get("isPlainVariant"));
    }

    @Test
    void aModelWithoutAUnionIsLeftAlone() {
        BetterKotlinCodegen.nameUnionVariants(new CodegenModel());
    }

    /* F862: an authenticator reads the variable the shared generator named for each credential. */
    @Test
    void credentialVariablesAreReadByFieldName() {
        Map<String, Object> ctx =
                Map.of(
                        "envVars",
                        List.of(
                                Map.of("field", "token", "envName", "SHOP_TOKEN"),
                                Map.of("field", "apiKey", "envName", "SHOP_API_KEY")));

        Map<String, String> byField = BetterKotlinCodegen.envNamesByField(ctx);

        assertEquals("SHOP_TOKEN", byField.get("token"));
        assertEquals("SHOP_API_KEY", byField.get("apiKey"));
        assertEquals(2, byField.size());
    }

    @Test
    void aSchemeWithoutCredentialVariablesHasNone() {
        assertTrue(BetterKotlinCodegen.envNamesByField(Map.of()).isEmpty());
    }

    /* F857: the client names each scheme credential after the scheme and imports its authenticator. */
    @Test
    void clientSchemesCarryTheArgumentNameAndTheAuthenticatorPackage(@TempDir Path out) {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        codegen.setOutputDir(out.toString());
        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        components.addSecuritySchemes(
                "api_key", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));
        components.addSecuritySchemes(
                "object", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"));
        components.addSecuritySchemes(
                "machine",
                new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                                new OAuthFlows()
                                        .clientCredentials(
                                                new OAuthFlow().tokenUrl("https://auth.example.com/token"))));
        openAPI.setComponents(components);

        codegen.processOpenAPI(openAPI);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schemes =
                (List<Map<String, Object>>) codegen.additionalProperties().get("clientSchemes");
        assertNotNull(schemes);
        assertEquals(3, schemes.size());
        assertEquals("apiKey", schemes.get(0).get("argName"));
        assertEquals("org.openapitools.auth", schemes.get(0).get("authPackage"));
        assertEquals("`object`", schemes.get(1).get("argName"));
        assertEquals("object", schemes.get(1).get("paramName"));
        assertEquals("machine", schemes.get(2).get("argName"));
        assertEquals("org.openapitools.auth.oauth", schemes.get(2).get("authPackage"));
        assertEquals(true, codegen.additionalProperties().get("hasClientSchemes"));
    }

    @Test
    void aSpecWithoutSecuritySchemesHasNoClientSchemes(@TempDir Path out) {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        codegen.setOutputDir(out.toString());
        codegen.processOpenAPI(new OpenAPI());

        assertEquals(List.of(), codegen.additionalProperties().get("clientSchemes"));
        assertEquals(false, codegen.additionalProperties().get("hasClientSchemes"));
    }
}
