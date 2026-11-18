package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Makes OAS 3.1 {@code const} usable: a {@code const} with no {@code type} gets the type of its
 * value (so it reaches the existing single-value enum path), and a {@code oneOf} whose members
 * are all {@code const} values (the JSON Schema enum idiom) becomes one {@code enum}.
 */
public class NormalizeConstRule extends SchemaWalkingRule {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        System.err.println("CONST " + schema.getClass().getSimpleName() + " " + schema.getName() + " " + schema.getProperties() + " " + schema.getConst() + " " + schema.getType() + " " + schema.getTypes() + " " + schema.getOneOf());
        if (schema.getConst() != null && schema.getType() == null && schema.getTypes() == null) {
            final String inferred = typeOf(schema.getConst());
            if (inferred != null) {
                schema.setType(inferred);
            }
        }
        final List<Schema> oneOf = schema.getOneOf();
        if (oneOf == null || oneOf.isEmpty()) {
            return;
        }
        final List<Object> values = new ArrayList<>();
        final Set<String> types = new LinkedHashSet<>();
        for (Schema<?> member : oneOf) {
            if (member.get$ref() != null || member.getConst() == null) {
                return;
            }
            final String type = member.getType() != null ? member.getType() : typeOf(member.getConst());
            if (type == null) {
                return;
            }
            types.add(type);
            values.add(member.getConst());
        }
        if (types.size() != 1) {
            return;
        }
        schema.setOneOf(null);
        schema.setType(types.iterator().next());
        schema.setEnum((List) values);
        logger.debug("Rewrote a oneOf of {} constants to an enum.", values.size());
    }

    @Nullable
    static String typeOf(Object value) {
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            return "integer";
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return "number";
        }
        return null;
    }
}
