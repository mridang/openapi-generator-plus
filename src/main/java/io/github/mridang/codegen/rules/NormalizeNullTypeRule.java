package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.slf4j.Logger;

/**
 * A schema whose only type is {@code null} (OAS 3.1 {@code type: "null"}) has no type in any
 * of the twelve languages, so it is rewritten to an untyped nullable schema, which every
 * language already maps to its any type.
 */
public class NormalizeNullTypeRule extends SchemaWalkingRule {

    private static final String NULL = "null";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        final Set<String> types = (Set<String>) (Set) schema.getTypes();
        final boolean only =
                NULL.equals(schema.getType()) || (types != null && Set.of(NULL).equals(types));
        if (!only) {
            return;
        }
        schema.setType(null);
        schema.setTypes(null);
        schema.setNullable(true);
        logger.debug("Rewrote a schema of type null to an untyped nullable schema.");
    }
}
