package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes OAS 3.1 / JSON Schema 2020-12 {@code prefixItems} (positional
 * tuple arrays) onto the OAS 3.0 homogeneous-array codepath so that every
 * downstream language generator emits compiling code without per-language
 * template changes.
 *
 * <p>A {@code prefixItems} schema declares a fixed sequence of per-index
 * types, e.g.:
 * <pre>
 *   type: array
 *   prefixItems:
 *     - type: string
 *     - type: integer
 *     - type: boolean
 * </pre>
 * No mainstream client codegen has a portable representation for a
 * heterogeneous fixed-arity tuple, so this rule degrades the schema to a
 * plain array of {@code Object}/{@code Any}/{@code interface{}} and appends
 * a description annotation recording the original per-index types so callers
 * (and humans) can still see the intended layout.
 *
 * <p>Translation:
 * <ul>
 *   <li>{@code prefixItems: [A, B, C]} on an array schema becomes
 *       {@code type: array, items: {}} (an empty object schema, which every
 *       language template maps to its "any" type)</li>
 *   <li>The schema description gains a line:
 *       {@code "Tuple of N positional items: [type1, type2, ...]"}</li>
 *   <li>{@code prefixItems} is then cleared so the rest of the codegen
 *       pipeline never sees it</li>
 * </ul>
 *
 * <p>This is the minimal-impact cross-language strategy. Idiomatic per-lang
 * tuple emission (Python {@code NamedTuple}, Kotlin {@code Pair}/data class,
 * Rust tuple struct, Swift typed tuple, etc.) is a follow-up; see Gap AZ in
 * AGENT.md.
 */
public class NormalizePrefixItemsRule implements CustomNormalizationRule {

    private static final String ARRAY_TYPE = "array";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting NORMALIZE_PREFIX_ITEMS rule (OAS 3.1 prefixItems tuple arrays).");

        Set<Schema<?>> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            for (Schema<?> schema : openAPI.getComponents().getSchemas().values()) {
                walk(schema, visited, logger);
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getParameters() != null) {
            for (Parameter parameter : openAPI.getComponents().getParameters().values()) {
                if (parameter != null) {
                    walk(parameter.getSchema(), visited, logger);
                    walkContent(parameter.getContent(), visited, logger);
                }
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getRequestBodies() != null) {
            for (RequestBody requestBody : openAPI.getComponents().getRequestBodies().values()) {
                if (requestBody != null) {
                    walkContent(requestBody.getContent(), visited, logger);
                }
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getResponses() != null) {
            for (ApiResponse apiResponse : openAPI.getComponents().getResponses().values()) {
                if (apiResponse != null) {
                    walkContent(apiResponse.getContent(), visited, logger);
                }
            }
        }

        if (openAPI.getPaths() != null) {
            for (PathItem pathItem : openAPI.getPaths().values()) {
                if (pathItem == null) {
                    continue;
                }
                if (pathItem.getParameters() != null) {
                    for (Parameter parameter : pathItem.getParameters()) {
                        if (parameter != null) {
                            walk(parameter.getSchema(), visited, logger);
                            walkContent(parameter.getContent(), visited, logger);
                        }
                    }
                }
                for (Operation operation : pathItem.readOperations()) {
                    walkOperation(operation, visited, logger);
                }
            }
        }

        logger.info("NORMALIZE_PREFIX_ITEMS rule completed.");
    }

    private void walkOperation(Operation operation, Set<Schema<?>> visited, Logger logger) {
        if (operation == null) {
            return;
        }
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                if (parameter != null) {
                    walk(parameter.getSchema(), visited, logger);
                    walkContent(parameter.getContent(), visited, logger);
                }
            }
        }
        if (operation.getRequestBody() != null) {
            walkContent(operation.getRequestBody().getContent(), visited, logger);
        }
        if (operation.getResponses() != null) {
            for (ApiResponse apiResponse : operation.getResponses().values()) {
                if (apiResponse != null) {
                    walkContent(apiResponse.getContent(), visited, logger);
                }
            }
        }
    }

    private void walkContent(@Nullable Content content, Set<Schema<?>> visited, Logger logger) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType != null) {
                walk(mediaType.getSchema(), visited, logger);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void walk(@Nullable Schema schema, Set<Schema<?>> visited, Logger logger) {
        if (schema == null || schema.get$ref() != null) {
            return;
        }
        if (!visited.add(schema)) {
            return;
        }

        applyToSchema(schema, logger);

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Schema<?> child : properties.values()) {
                walk(child, visited, logger);
            }
        }
        if (schema.getItems() != null) {
            walk(schema.getItems(), visited, logger);
        }
        Object addProps = schema.getAdditionalProperties();
        if (addProps instanceof Schema) {
            walk((Schema<?>) addProps, visited, logger);
        }
        walkList(schema.getAllOf(), visited, logger);
        walkList(schema.getAnyOf(), visited, logger);
        walkList(schema.getOneOf(), visited, logger);
        if (schema.getNot() != null) {
            walk(schema.getNot(), visited, logger);
        }
    }

    @SuppressWarnings({"rawtypes"})
    private void walkList(@Nullable List<Schema> list, Set<Schema<?>> visited, Logger logger) {
        if (list == null) {
            return;
        }
        for (Schema<?> s : new ArrayList<Schema>(list)) {
            walk(s, visited, logger);
        }
    }

    /**
     * Degrades a single schema's {@code prefixItems} to a plain array-of-Object,
     * preserving the original per-index types in the description. Idempotent:
     * clears {@code prefixItems} so re-running the rule has no effect.
     */
    @SuppressWarnings({"rawtypes"})
    private void applyToSchema(Schema<?> schema, Logger logger) {
        List<Schema> prefixItems = schema.getPrefixItems();
        if (prefixItems == null || prefixItems.isEmpty()) {
            return;
        }

        StringBuilder summary = new StringBuilder("Tuple of ")
                .append(prefixItems.size())
                .append(" positional items: [");
        for (int i = 0; i < prefixItems.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(describePrefixItem(prefixItems.get(i)));
        }
        summary.append("]");

        // Force this schema onto the homogeneous-array codepath. Set both the
        // singular `type` field (used by most generators) and clear the 3.1
        // `types` set, then replace `items` with an empty object schema which
        // every language template maps to its "any" type (Object, Any,
        // interface{}, etc.).
        schema.setType(ARRAY_TYPE);
        schema.setTypes(null);
        schema.setItems(new ObjectSchema());
        schema.setPrefixItems(null);

        // Some 3.1 specs also use `items: false` or `items: {}` alongside
        // prefixItems to forbid extra elements; we don't enforce that here.

        appendDocstring(schema, summary.toString());
        logger.debug("Normalized prefixItems tuple ({}) on schema '{}'.",
                summary, schema.getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String describePrefixItem(@Nullable Schema item) {
        if (item == null) {
            return "any";
        }
        if (item.get$ref() != null) {
            String ref = item.get$ref();
            int slash = ref.lastIndexOf('/');
            return slash >= 0 ? ref.substring(slash + 1) : ref;
        }
        String type = item.getType();
        Set<String> types = (Set<String>) item.getTypes();
        if (type == null && types != null && !types.isEmpty()) {
            // Pick a stable label for union types.
            type = String.join("|", new java.util.TreeSet<>(types));
        }
        if (type == null) {
            return "any";
        }
        String format = item.getFormat();
        if (format != null && !format.isEmpty()) {
            return type + "(" + format + ")";
        }
        return type;
    }

    private void appendDocstring(Schema<?> schema, String addition) {
        String existing = schema.getDescription();
        if (existing == null || existing.isEmpty()) {
            schema.setDescription(addition);
        } else if (!existing.contains(addition)) {
            schema.setDescription(existing + "\n" + addition);
        }
    }
}
