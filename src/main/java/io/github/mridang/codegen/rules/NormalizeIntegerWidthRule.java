package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.slf4j.Logger;

/**
 * OpenAPI integers are unbounded, so a schema of {@code type: integer} with no {@code format}
 * is a 64-bit integer in all twelve SDKs (decision D113). The stock mapping would give it the
 * same type as {@code int32}; writing {@code format: int64} on it first makes every language
 * use the path it already has for a 64-bit integer, including the range check on decode.
 */
public class NormalizeIntegerWidthRule extends SchemaWalkingRule {

    private static final String INTEGER = "integer";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        final Set<String> types = (Set<String>) (Set) schema.getTypes();
        final boolean integer =
                INTEGER.equals(schema.getType()) || (types != null && Set.of(INTEGER).equals(types));
        if (integer && schema.getFormat() == null) {
            schema.setFormat("int64");
            logger.debug("Gave an unformatted integer schema the format int64.");
        }
    }
}
