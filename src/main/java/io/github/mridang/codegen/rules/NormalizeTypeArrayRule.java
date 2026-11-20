package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
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
 */
public class NormalizeTypeArrayRule extends SchemaWalkingRule {

    private static final String NULL = "null";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        final Set<String> declared = (Set<String>) (Set) schema.getTypes();
        if (declared == null || declared.size() < 2 || schema.getAnyOf() != null
                || schema.getOneOf() != null || schema.getEnum() != null) {
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
}
