package io.github.mridang.codegen.rules;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Makes the JSON Schema 2020-12 {@code $defs} of a component schema usable (#82). The pinned
 * parser has no field for {@code $defs} and keeps it as a raw map among the schema's extensions,
 * so a {@code $ref} such as {@code #/components/schemas/Order/$defs/Line} names nothing the
 * generators can see. Each definition becomes a component schema of its own, named after the
 * definition ({@code Line}) unless that name is taken, in which case the owner's name is put in
 * front ({@code OrderLine}); every reference to it is rewritten to the new component, and the
 * {@code $defs} map is removed. Definitions nested in a definition are hoisted the same way.
 */
public class HoistDefsRule implements CustomNormalizationRule {

    private static final String DEFS = "$defs";
    private static final String COMPONENT_PREFIX = "#/components/schemas/";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return;
        }
        final Map<String, Schema> components = openAPI.getComponents().getSchemas();
        final Map<String, String> moved = new LinkedHashMap<>();
        final List<String> pending = new ArrayList<>(components.keySet());
        while (!pending.isEmpty()) {
            final String owner = pending.remove(0);
            final Schema<?> schema = components.get(owner);
            if (schema == null) {
                continue;
            }
            for (String hoisted : hoist(owner, schema, components, moved, logger)) {
                pending.add(hoisted);
            }
        }
        if (moved.isEmpty()) {
            return;
        }
        for (Schema<?> schema : components.values()) {
            rewrite(schema, moved);
        }
        new RewriteReferences(moved).apply(openAPI, ruleConfig, logger);
    }

    /** Rewrites the references into hoisted definitions held anywhere in the document. */
    private static final class RewriteReferences extends SchemaWalkingRule {

        private final Map<String, String> moved;

        RewriteReferences(Map<String, String> moved) {
            this.moved = moved;
        }

        @Override
        protected void visit(Schema<?> schema, Logger logger) {
            rewriteChildren(schema, moved);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> hoist(
            String owner,
            Schema<?> schema,
            Map<String, Schema> components,
            Map<String, String> moved,
            Logger logger) {
        final List<String> hoisted = new ArrayList<>();
        final Map<String, Object> extensions = schema.getExtensions();
        if (extensions == null || !(extensions.get(DEFS) instanceof Map)) {
            return hoisted;
        }
        final Map<String, Object> defs = (Map<String, Object>) extensions.remove(DEFS);
        if (extensions.isEmpty()) {
            schema.setExtensions(null);
        }
        for (Map.Entry<String, Object> def : defs.entrySet()) {
            final String name = components.containsKey(def.getKey())
                    ? owner + capitalise(def.getKey())
                    : def.getKey();
            components.put(name, Json31.mapper().convertValue(def.getValue(), Schema.class));
            moved.put(COMPONENT_PREFIX + owner + "/" + DEFS + "/" + def.getKey(), COMPONENT_PREFIX + name);
            hoisted.add(name);
            logger.debug("Hoisted $defs/{} of '{}' to the component '{}'.", def.getKey(), owner, name);
        }
        return hoisted;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewrite(@Nullable Schema schema, Map<String, String> moved) {
        if (schema == null) {
            return;
        }
        final String target = schema.get$ref() == null ? null : moved.get(schema.get$ref());
        if (target != null) {
            schema.set$ref(target);
        }
        rewriteChildren(schema, moved);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteChildren(Schema<?> schema, Map<String, String> moved) {
        if (schema.getProperties() != null) {
            for (Schema child : schema.getProperties().values()) {
                rewriteRef(child, moved);
            }
        }
        rewriteRef(schema.getItems(), moved);
        if (schema.getAdditionalProperties() instanceof Schema) {
            rewriteRef((Schema) schema.getAdditionalProperties(), moved);
        }
        rewriteRefs(schema.getAllOf(), moved);
        rewriteRefs(schema.getAnyOf(), moved);
        rewriteRefs(schema.getOneOf(), moved);
        rewriteRefs(schema.getPrefixItems(), moved);
        rewriteRef(schema.getNot(), moved);
    }

    @SuppressWarnings("rawtypes")
    private static void rewriteRefs(@Nullable List<Schema> schemas, Map<String, String> moved) {
        if (schemas != null) {
            for (Schema schema : schemas) {
                rewriteRef(schema, moved);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void rewriteRef(@Nullable Schema schema, Map<String, String> moved) {
        if (schema != null && schema.get$ref() != null && moved.containsKey(schema.get$ref())) {
            schema.set$ref(moved.get(schema.get$ref()));
        }
    }

    private static String capitalise(String name) {
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
