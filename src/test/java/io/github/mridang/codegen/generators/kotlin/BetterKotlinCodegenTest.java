package io.github.mridang.codegen.generators.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openapitools.codegen.CodegenProperty;

import org.junit.jupiter.api.Test;

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
}
