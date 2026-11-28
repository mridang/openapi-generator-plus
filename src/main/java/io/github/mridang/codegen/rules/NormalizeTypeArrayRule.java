package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Rewrites an OAS 3.1 {@code type: [A, B, ...]} (with {@code null} already counting as
 * nullability) into an {@code anyOf} with one schema per type, moving the keywords that belong
 * to a type onto that member: {@code items} to the array, {@code properties}, {@code required}
 * and {@code additionalProperties} to the object, {@code format} to the scalar members.
 *
 * <p>The stock normaliser runs first and rewrites a type array of scalars into an {@code anyOf}
 * of its own {@code IntegerSchema}, {@code StringSchema} and similar members while leaving the
 * type array in place. Those members carry library defaults ({@code IntegerSchema} is
 * {@code int32}, which would break D113), so such an {@code anyOf} is replaced by the one this
 * rule builds (#98).
 */
public class NormalizeTypeArrayRule extends SchemaWalkingRule {

    private static final String NULL = "null";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        final Set<String> declared = (Set<String>) (Set) schema.getTypes();
        if (declared == null || declared.size() < 2 || schema.getOneOf() != null
                || schema.getEnum() != null
                || (schema.getAnyOf() != null && !synthesized(schema.getAnyOf(), declared))) {
            return;
        }
        final Set<String> types = new LinkedHashSet<>(declared);
        if (types.remove(NULL)) {
            schema.setNullable(true);
        }
        if (types.size() < 2) {
            schema.setTypes(null);
            schema.setType(types.isEmpty() ? null : types.iterator().next());
            return;
        }
        final List<Schema> members = new ArrayList<>();
        for (String type : types) {
            final Schema member = new Schema();
            member.setType(type);
            switch (type) {
                case "array":
                    member.setItems(schema.getItems());
                    break;
                case "object":
                    member.setProperties((Map<String, Schema>) (Map) schema.getProperties());
                    member.setRequired(schema.getRequired());
                    member.setAdditionalProperties(schema.getAdditionalProperties());
                    break;
                default:
                    member.setFormat(schema.getFormat());
                    break;
            }
            members.add(member);
        }
        schema.setTypes(null);
        schema.setType(null);
        schema.setItems(null);
        schema.setProperties(null);
        schema.setRequired(null);
        schema.setAdditionalProperties(null);
        schema.setFormat(null);
        schema.setAnyOf(members);
        logger.debug("Rewrote a type array of {} types to an anyOf.", members.size());
    }

    /**
     * Whether an {@code anyOf} is the one the stock normaliser builds from a type array: one bare
     * scalar member per declared type, no reference, no keyword of its own besides the library
     * default format.
     */
    @SuppressWarnings("rawtypes")
    private static boolean synthesized(List<Schema> anyOf, Set<String> declared) {
        for (Schema member : anyOf) {
            final boolean scalar = member instanceof StringSchema || member instanceof IntegerSchema
                    || member instanceof NumberSchema || member instanceof BooleanSchema;
            if (!scalar || member.get$ref() != null || member.getProperties() != null
                    || member.getEnum() != null || !declared.contains(member.getType())) {
                return false;
            }
        }
        return true;
    }
}
