package io.github.mridang.codegen.rules;

import io.github.mridang.codegen.SpecWarnings;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;

/**
 * Logs one warning per JSON Schema keyword that the generator reads and ignores: the
 * validation-only keywords (the server owns validation), {@code patternProperties} and
 * {@code $anchor}. It never changes the document. Like the other spec warnings, the keywords
 * fail the run instead when {@code -DstrictSpec=true} is set.
 */
public class WarnUnsupportedKeywordsRule extends SchemaWalkingRule {

    private final Set<String> found = new TreeSet<>();

    /**
     * Warns about the unsupported keywords of every schema in the document, and fails the run
     * when the {@value SpecWarnings#STRICT_PROPERTY} system property is set.
     */
    public static void check(OpenAPI openAPI, Logger logger) {
        check(openAPI, Boolean.getBoolean(SpecWarnings.STRICT_PROPERTY), logger);
    }

    /**
     * Warns about the unsupported keywords of every schema in the document. When {@code strict}
     * is set and at least one keyword was found, the run fails after all of them were logged.
     *
     * @param openAPI The specification to inspect.
     * @param strict  Whether an unsupported keyword fails the run.
     * @param logger  The logger that receives the warnings.
     * @throws IllegalStateException when {@code strict} is set and a keyword was found.
     */
    public static void check(OpenAPI openAPI, boolean strict, Logger logger) {
        WarnUnsupportedKeywordsRule rule = new WarnUnsupportedKeywordsRule();
        rule.apply(openAPI, Map.of(), logger);
        if (strict && !rule.found.isEmpty()) {
            throw new IllegalStateException("The specification uses " + rule.found.size()
                + " unsupported keyword(s) and " + SpecWarnings.STRICT_PROPERTY + " is set: "
                + String.join(", ", rule.found));
        }
    }

    @Override
    protected void visit(Schema<?> schema, Logger logger) {
        note(schema.getNot() != null, "not", logger);
        note(schema.getContains() != null, "contains", logger);
        note(schema.getMinContains() != null, "minContains", logger);
        note(schema.getUnevaluatedItems() != null, "unevaluatedItems", logger);
        note(schema.getDependentRequired() != null, "dependentRequired", logger);
        note(schema.getDependentSchemas() != null, "dependentSchemas", logger);
        note(schema.getIf() != null || schema.getThen() != null || schema.getElse() != null,
                "if/then/else", logger);
        note(schema.getPropertyNames() != null, "propertyNames", logger);
        note(schema.getContentSchema() != null, "contentSchema", logger);
        note(schema.getPatternProperties() != null, "patternProperties", logger);
        note(schema.get$anchor() != null, "$anchor", logger);
    }

    private void note(boolean present, String keyword, Logger logger) {
        if (present && found.add(keyword)) {
            logger.warn("Spec warning: the keyword {} is not supported and is ignored.", keyword);
        }
    }
}
