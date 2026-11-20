package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;

/**
 * Logs one warning per JSON Schema keyword that the generator reads and ignores: the
 * validation-only keywords (the server owns validation), {@code patternProperties} and
 * {@code $anchor}. It never changes the document.
 */
public class WarnUnsupportedKeywordsRule extends SchemaWalkingRule {

    private final Set<String> found = new TreeSet<>();

    /** Warns about the unsupported keywords of every schema in the document. */
    public static void check(OpenAPI openAPI, Logger logger) {
        new WarnUnsupportedKeywordsRule().apply(openAPI, Map.of(), logger);
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
