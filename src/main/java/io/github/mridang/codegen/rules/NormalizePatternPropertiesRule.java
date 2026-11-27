package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Turns JSON Schema 2020-12 {@code patternProperties} into the typed extra-property map every
 * SDK already has for {@code additionalProperties} (#84, D185). The keys a pattern matches are
 * the keys a client cannot name in advance, which is exactly what the extra-property map holds,
 * so the value schema of the patterns becomes the {@code additionalProperties} schema:
 *
 * <ul>
 *   <li>One pattern, or several with the same value schema: that schema types the extras.</li>
 *   <li>Patterns with different value schemas: the extras stay free-form.</li>
 *   <li>An {@code additionalProperties} schema equal to the pattern schema is kept; a different
 *       one, or {@code true}, leaves the extras free-form; {@code false} or none is replaced by the
 *       pattern schema, since the keys the patterns admit are extras.</li>
 * </ul>
 *
 * The key patterns themselves, and {@code propertyNames}, are not enforced, like every other
 * validation keyword (S3); {@code propertyNames} is left in place, so it is still reported as
 * ignored.
 */
public class NormalizePatternPropertiesRule extends SchemaWalkingRule {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        final Map<String, Schema> patterns = schema.getPatternProperties();
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        final List<Schema> values = new ArrayList<>(patterns.values());
        final Schema first = values.get(0);
        final boolean uniform = values.stream().allMatch(first::equals);
        Object declared = schema.getAdditionalProperties();
        if (declared instanceof Schema && ((Schema) declared).getBooleanSchemaValue() != null) {
            // OAS 3.1 parses `additionalProperties: true` and `false` as boolean schemas.
            declared = ((Schema) declared).getBooleanSchemaValue();
        }
        final Object extras;
        if (!uniform || Boolean.TRUE.equals(declared)) {
            extras = true;
        } else if (declared instanceof Schema && !first.equals(declared)) {
            extras = true;
        } else {
            extras = first;
        }
        schema.setAdditionalProperties(extras);
        schema.setPatternProperties(null);
        if (schema.getType() == null && schema.getTypes() == null) {
            schema.setType("object");
        }
        logger.debug("Typed the extras of a schema by its {} pattern(s).", patterns.size());
    }
}
