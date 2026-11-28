package io.github.mridang.codegen.generators;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;

/**
 * The opt-in client-side check of the JSON Schema validation keywords (D189, issue 83).
 *
 * <p>Reads the keywords of every model property from the raw schema (the generator's own
 * {@link CodegenProperty} fields lose the OAS 3.1 numeric {@code exclusiveMinimum} and escape the
 * pattern) and hands each language a ready list: on the model, {@code
 * vendorExtensions.constraintProps} (one entry per property that has a check or holds a model that
 * has one) and {@code vendorExtensions.hasConstraintValidation}; on the generator, the list of the
 * models that have one, which the language's constraint validator file iterates. The message after
 * the path is computed here so it is the same text in all twelve SDKs.
 */
public final class ConstraintChecks {

    /** The prefix of every message; the path and the rule follow it. */
    public static final String MESSAGE_PREFIX = "Invalid request body: ";

    private ConstraintChecks() {}

    /**
     * Marks every model that has a constraint check, directly or through a model it holds, and
     * returns those models in the order they were seen.
     *
     * @param models the processed models
     * @param openAPI the document being generated, for the raw property schemas
     * @return the models that carry {@code hasConstraintValidation}
     */
    public static List<CodegenModel> mark(Map<String, ModelsMap> models, @Nullable OpenAPI openAPI) {
        final Map<String, CodegenModel> byClass = new LinkedHashMap<>();
        for (final ModelsMap modelsMap : models.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (isPlainObject(model)) {
                    byClass.put(model.classname, model);
                }
            }
        }
        final Map<String, List<Entry>> own = new HashMap<>();
        for (final CodegenModel model : byClass.values()) {
            final Map<String, Schema<?>> raw = rawProperties(openAPI, model.schemaName);
            final List<Entry> entries = new ArrayList<>();
            for (final CodegenProperty prop : model.allVars) {
                entries.add(new Entry(prop, checksFor(prop, raw.get(prop.baseName))));
            }
            own.put(model.classname, entries);
        }
        // Fixpoint: a model validates when it has a check or holds a model that validates.
        final Set<String> validating = new LinkedHashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (final Map.Entry<String, List<Entry>> model : own.entrySet()) {
                if (validating.contains(model.getKey())) {
                    continue;
                }
                for (final Entry entry : model.getValue()) {
                    if (!entry.checks().isEmpty()
                            || holds(validating, nestedModel(entry.prop()))
                            || holds(validating, nestedItemsModel(entry.prop()))) {
                        validating.add(model.getKey());
                        changed = true;
                        break;
                    }
                }
            }
        }
        final List<CodegenModel> result = new ArrayList<>();
        for (final CodegenModel model : byClass.values()) {
            final boolean validates = validating.contains(model.classname);
            model.vendorExtensions.put("hasConstraintValidation", validates);
            if (!validates) {
                continue;
            }
            final List<Map<String, Object>> props = new ArrayList<>();
            for (final Entry entry : own.getOrDefault(model.classname, List.of())) {
                final CodegenProperty prop = entry.prop();
                final String nested = nestedModel(prop);
                final String nestedItems = nestedItemsModel(prop);
                final boolean hasNested = holds(validating, nested);
                final boolean hasNestedItems = holds(validating, nestedItems);
                if (entry.checks().isEmpty() && !hasNested && !hasNestedItems) {
                    continue;
                }
                final Map<String, Object> view = new HashMap<>();
                view.put("prop", prop);
                view.put("name", prop.name);
                view.put("baseName", prop.baseName);
                view.put("getter", prop.getter);
                view.put("checks", entry.checks());
                view.put("hasChecks", !entry.checks().isEmpty());
                view.put("hasNestedModel", hasNested);
                view.put("hasNestedItemsModel", hasNestedItems);
                if (hasNested) {
                    view.put("nestedModel", nested);
                }
                if (hasNestedItems) {
                    view.put("nestedItemsModel", nestedItems);
                }
                props.add(view);
            }
            model.vendorExtensions.put("constraintProps", props);
            result.add(model);
        }
        return result;
    }

    /** One property of a model and its checks. */
    private static final class Entry {
        private final CodegenProperty prop;
        private final List<Map<String, Object>> checks;

        Entry(CodegenProperty prop, List<Map<String, Object>> checks) {
            this.prop = prop;
            this.checks = checks;
        }

        CodegenProperty prop() {
            return prop;
        }

        List<Map<String, Object>> checks() {
            return checks;
        }
    }

    private static boolean holds(Set<String> validating, @Nullable String name) {
        return name != null && validating.contains(name);
    }

    /** A model with named properties: not an enum, a union, an alias or a container. */
    private static boolean isPlainObject(CodegenModel model) {
        return !model.isEnum
                && (model.oneOf == null || model.oneOf.isEmpty())
                && (model.anyOf == null || model.anyOf.isEmpty())
                && !model.isAlias
                && !model.isArray
                && model.allVars != null;
    }

    @Nullable
    private static String nestedModel(CodegenProperty prop) {
        return prop.isModel && !prop.isContainer ? prop.complexType : null;
    }

    @Nullable
    private static String nestedItemsModel(CodegenProperty prop) {
        return prop.isArray && prop.items != null && prop.items.isModel ? prop.items.complexType : null;
    }

    /**
     * The checks a property gets, in a fixed order: string keywords, numeric keywords, array
     * keywords, map keywords. A keyword is checked only on the kind of value it applies to.
     */
    static List<Map<String, Object>> checksFor(CodegenProperty prop, @Nullable Schema<?> schema) {
        final List<Map<String, Object>> checks = new ArrayList<>();
        if (schema == null) {
            return checks;
        }
        if (isPlainString(prop)) {
            if (schema.getMinLength() != null) {
                checks.add(check("minLength", schema.getMinLength().toString(),
                        "must be at least " + schema.getMinLength() + " characters long"));
            }
            if (schema.getMaxLength() != null) {
                checks.add(check("maxLength", schema.getMaxLength().toString(),
                        "must be at most " + schema.getMaxLength() + " characters long"));
            }
            if (schema.getPattern() != null && !schema.getPattern().isEmpty()) {
                final Map<String, Object> c = check("pattern", schema.getPattern(),
                        "must match the pattern " + schema.getPattern());
                c.put("pattern", schema.getPattern());
                checks.add(c);
            }
        }
        if (isPlainNumber(prop)) {
            final BigDecimal exclusiveMin = schema.getExclusiveMinimumValue();
            final BigDecimal exclusiveMax = schema.getExclusiveMaximumValue();
            // OAS 3.1 numeric bounds; the parser may also mirror one into minimum plus the 3.0
            // boolean, so the boolean form counts only when no numeric bound is present.
            if (exclusiveMin != null) {
                checks.add(number("exclusiveMinimum", exclusiveMin, "must be greater than "));
            }
            if (schema.getMinimum() != null && !Boolean.TRUE.equals(schema.getExclusiveMinimum())) {
                checks.add(number("minimum", schema.getMinimum(), "must be greater than or equal to "));
            } else if (schema.getMinimum() != null && exclusiveMin == null) {
                checks.add(number("exclusiveMinimum", schema.getMinimum(), "must be greater than "));
            }
            if (exclusiveMax != null) {
                checks.add(number("exclusiveMaximum", exclusiveMax, "must be less than "));
            }
            if (schema.getMaximum() != null && !Boolean.TRUE.equals(schema.getExclusiveMaximum())) {
                checks.add(number("maximum", schema.getMaximum(), "must be less than or equal to "));
            } else if (schema.getMaximum() != null && exclusiveMax == null) {
                checks.add(number("exclusiveMaximum", schema.getMaximum(), "must be less than "));
            }
            if (schema.getMultipleOf() != null && schema.getMultipleOf().signum() > 0) {
                checks.add(number("multipleOf", schema.getMultipleOf(), "must be a multiple of "));
            }
        }
        if (prop.isArray) {
            if (schema.getMinItems() != null) {
                checks.add(check("minItems", schema.getMinItems().toString(),
                        "must have at least " + schema.getMinItems() + " items"));
            }
            if (schema.getMaxItems() != null) {
                checks.add(check("maxItems", schema.getMaxItems().toString(),
                        "must have at most " + schema.getMaxItems() + " items"));
            }
        }
        if (prop.isMap) {
            if (schema.getMinProperties() != null) {
                checks.add(check("minProperties", schema.getMinProperties().toString(),
                        "must have at least " + schema.getMinProperties() + " properties"));
            }
            if (schema.getMaxProperties() != null) {
                checks.add(check("maxProperties", schema.getMaxProperties().toString(),
                        "must have at most " + schema.getMaxProperties() + " properties"));
            }
        }
        return checks;
    }

    private static boolean isPlainString(CodegenProperty prop) {
        return prop.isString && !prop.isContainer && !prop.isEnum && !prop.isEnumRef
                && !prop.isUuid && !prop.isUri && !prop.isDate && !prop.isDateTime
                && !prop.isByteArray && !prop.isBinary && !prop.isDecimal;
    }

    private static boolean isPlainNumber(CodegenProperty prop) {
        return (prop.isInteger || prop.isLong || prop.isNumber || prop.isFloat || prop.isDouble)
                && !prop.isContainer && !prop.isEnum && !prop.isEnumRef && !prop.isDecimal;
    }

    private static Map<String, Object> number(String keyword, BigDecimal limit, String rule) {
        final String text = plain(limit);
        final Map<String, Object> c = check(keyword, text, rule + text);
        c.put("limitDouble", text.contains(".") ? text : text + ".0");
        c.put("limitIsInteger", !text.contains("."));
        return c;
    }

    private static Map<String, Object> check(String keyword, String limit, String message) {
        final Map<String, Object> c = new HashMap<>();
        c.put("keyword", keyword);
        c.put("is" + Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1), true);
        c.put("limit", limit);
        c.put("message", message);
        return c;
    }

    /** The plain decimal text of a limit: no exponent and no trailing zeros. */
    static String plain(BigDecimal value) {
        final BigDecimal stripped = value.stripTrailingZeros();
        return stripped.signum() == 0 ? "0" : stripped.toPlainString();
    }

    /** The properties of a component schema, its allOf members' included, references resolved. */
    private static Map<String, Schema<?>> rawProperties(@Nullable OpenAPI openAPI, @Nullable String name) {
        final Map<String, Schema<?>> out = new HashMap<>();
        if (openAPI == null || name == null || openAPI.getComponents() == null
                || openAPI.getComponents().getSchemas() == null) {
            return out;
        }
        collect(openAPI, openAPI.getComponents().getSchemas().get(name), out, new LinkedHashSet<>());
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collect(OpenAPI openAPI, @Nullable Schema schema, Map<String, Schema<?>> out,
            Set<String> seen) {
        final Schema resolved = resolve(openAPI, schema, seen);
        if (resolved == null) {
            return;
        }
        if (resolved.getAllOf() != null) {
            for (final Object member : resolved.getAllOf()) {
                collect(openAPI, (Schema) member, out, seen);
            }
        }
        if (resolved.getProperties() != null) {
            for (final Object e : resolved.getProperties().entrySet()) {
                final Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) e;
                final Schema prop = resolve(openAPI, entry.getValue(), new LinkedHashSet<>());
                if (prop != null) {
                    out.put(entry.getKey(), prop);
                }
            }
        }
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    private static Schema resolve(OpenAPI openAPI, @Nullable Schema schema, Set<String> seen) {
        Schema current = schema;
        while (current != null && current.get$ref() != null) {
            final String ref = current.get$ref();
            if (!seen.add(ref) || !ref.startsWith("#/components/schemas/")
                    || openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
                return null;
            }
            current = openAPI.getComponents().getSchemas().get(ref.substring("#/components/schemas/".length()));
        }
        return current;
    }
}
