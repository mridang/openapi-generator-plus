package io.github.mridang.codegen.generators.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("pathParam", codegen.toParamName("path"));
        assertEquals("urlParam", codegen.toParamName("url"));
    }

    @Test
    void otherParametersKeepTheirName() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("itemId", codegen.toParamName("item_id"));
        assertEquals("status", codegen.toParamName("status"));
    }
}
