package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Gives a schema with JSON Schema 2020-12 {@code if}/{@code then}/{@code else} one model shape
 * (#86, D186). A client cannot know which branch a payload takes before it reads it, so the
 * properties that either branch declares become optional properties of the schema itself, in
 * the order {@code then} before {@code else}. A property the schema already declares keeps its
 * own schema; a property both branches declare with different schemas keeps the {@code then}
 * one and is logged. The condition and the branches' {@code required} lists are not enforced
 * (S3), and {@code if}, {@code then} and {@code else} are removed afterwards.
 */
public class MergeConditionalPropertiesRule extends SchemaWalkingRule {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void visit(Schema<?> schema, Logger logger) {
        if (schema.getThen() == null && schema.getElse() == null) {
            if (schema.getIf() != null) {
                schema.setIf(null);
            }
            return;
        }
        final Map<String, Schema> merged = new LinkedHashMap<>();
        if (schema.getProperties() != null) {
            merged.putAll(schema.getProperties());
        }
        final Map<String, Schema> fromThen = new LinkedHashMap<>();
        add(merged, fromThen, schema.getThen(), logger);
        add(merged, fromThen, schema.getElse(), logger);
        schema.setProperties(merged);
        if (schema.getType() == null && schema.getTypes() == null) {
            schema.setType("object");
        }
        schema.setIf(null);
        schema.setThen(null);
        schema.setElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void add(
            Map<String, Schema> merged, Map<String, Schema> branchProperties, @Nullable Schema branch, Logger logger) {
        if (branch == null || branch.getProperties() == null) {
            return;
        }
        for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) branch.getProperties()).entrySet()) {
            final Schema existing = merged.get(entry.getKey());
            if (existing == null) {
                merged.put(entry.getKey(), entry.getValue());
                branchProperties.put(entry.getKey(), entry.getValue());
            } else if (branchProperties.containsKey(entry.getKey()) && !existing.equals(entry.getValue())) {
                logger.warn(
                        "Spec warning: the property {} is declared with different schemas in then and"
                                + " else; the then schema is used.",
                        entry.getKey());
            }
        }
    }
}
