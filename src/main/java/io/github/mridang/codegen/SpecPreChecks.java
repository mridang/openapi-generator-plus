package io.github.mridang.codegen;

import io.github.mridang.codegen.rules.HoistDefsRule;
import io.github.mridang.codegen.rules.MergeConditionalPropertiesRule;
import io.github.mridang.codegen.rules.WarnUnsupportedKeywordsRule;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Collections;
import org.slf4j.Logger;

/**
 * The passes that must see the document as the parser left it, before the stock OpenAPI 3.1
 * normaliser rewrites it: the stock normaliser rebuilds every array schema and drops
 * {@code contains}, {@code minContains}, {@code maxContains} and {@code unevaluatedItems}, so
 * their warnings would never be logged, and the {@code $defs} hoist must run before anything
 * resolves a reference, and the properties of {@code then} and {@code else} are merged before
 * the inline model resolver names the inline schemas. {@link PreNormalizer} runs them first; the generator runs them again in
 * {@code preprocessOpenAPI} only when the normaliser did not (it is switched off, or the strict
 * check failed inside it, where the generator swallows the exception).
 */
public final class SpecPreChecks {

    private static final String DONE = "x-codegen-plus-prechecked";

    private SpecPreChecks() {}

    /** Hoists {@code $defs} and warns about the unsupported keywords, once per document. */
    public static void run(OpenAPI openAPI, Logger logger) {
        if (openAPI.getExtensions() != null && openAPI.getExtensions().containsKey(DONE)) {
            return;
        }
        new HoistDefsRule().apply(openAPI, Collections.emptyMap(), logger);
        new MergeConditionalPropertiesRule().apply(openAPI, Collections.emptyMap(), logger);
        WarnUnsupportedKeywordsRule.check(openAPI, logger);
        openAPI.addExtension(DONE, true);
    }

    /** Removes the marker {@link #run} leaves, so it never reaches a template. */
    public static void clear(OpenAPI openAPI) {
        if (openAPI.getExtensions() != null) {
            openAPI.getExtensions().remove(DONE);
            if (openAPI.getExtensions().isEmpty()) {
                openAPI.setExtensions(null);
            }
        }
    }
}
