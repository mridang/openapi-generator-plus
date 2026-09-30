package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes OAS 3.1 nullability syntax to the OAS 3.0 form understood by the
 * rest of the codegen pipeline.
 * <p>
 * In OAS 3.1, a nullable type is expressed as a type array containing
 * {@code "null"}, e.g. {@code type: ["string", "null"]}, replacing the OAS 3.0
 * {@code nullable: true} keyword. This rule walks every schema in the document
 * and, when it encounters a {@code types} set containing {@code "null"}, it:
 * <ul>
 *   <li>removes {@code "null"} from the {@code types} set;</li>
 *   <li>sets {@code nullable = true};</li>
 *   <li>if exactly one non-null type remains, collapses the {@code types} set
 *   into the singular {@code type} field and clears {@code types};</li>
 *   <li>otherwise leaves the (now null-free) {@code types} set on the schema.</li>
 * </ul>
 * The downstream OAS 3.0 nullable codepath then handles code generation as
 * usual.
 */
public class NormalizeOas31NullableTypeRule implements CustomNormalizationRule {

    private static final String NULL_TYPE = "null";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting NORMALIZE_OAS31_NULLABLE_TYPE rule.");

        // Track visited schemas by identity to avoid infinite recursion in
        // self-referential structures.
        Set<Schema<?>> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            for (Schema<?> schema : openAPI.getComponents().getSchemas().values()) {
                normalizeSchema(schema, visited, logger);
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getParameters() != null) {
            for (Parameter parameter : openAPI.getComponents().getParameters().values()) {
                if (parameter != null) {
                    normalizeSchema(parameter.getSchema(), visited, logger);
                    normalizeContent(parameter.getContent(), visited, logger);
                }
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getRequestBodies() != null) {
            for (RequestBody requestBody : openAPI.getComponents().getRequestBodies().values()) {
                if (requestBody != null) {
                    normalizeContent(requestBody.getContent(), visited, logger);
                }
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getResponses() != null) {
            for (ApiResponse apiResponse : openAPI.getComponents().getResponses().values()) {
                if (apiResponse != null) {
                    normalizeContent(apiResponse.getContent(), visited, logger);
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
                            normalizeSchema(parameter.getSchema(), visited, logger);
                            normalizeContent(parameter.getContent(), visited, logger);
                        }
                    }
                }
                for (Operation operation : pathItem.readOperations()) {
                    normalizeOperation(operation, visited, logger);
                }
            }
        }

        logger.info("NORMALIZE_OAS31_NULLABLE_TYPE rule completed.");
    }

    private void normalizeOperation(Operation operation, Set<Schema<?>> visited, Logger logger) {
        if (operation == null) {
            return;
        }
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                if (parameter != null) {
                    normalizeSchema(parameter.getSchema(), visited, logger);
                    normalizeContent(parameter.getContent(), visited, logger);
                }
            }
        }
        if (operation.getRequestBody() != null) {
            normalizeContent(operation.getRequestBody().getContent(), visited, logger);
        }
        if (operation.getResponses() != null) {
            for (ApiResponse apiResponse : operation.getResponses().values()) {
                if (apiResponse != null) {
                    normalizeContent(apiResponse.getContent(), visited, logger);
                }
            }
        }
    }

    private void normalizeContent(@Nullable Content content, Set<Schema<?>> visited, Logger logger) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType != null) {
                normalizeSchema(mediaType.getSchema(), visited, logger);
            }
        }
    }

    /**
     * Recursively walks the given schema, normalizing any OAS 3.1 nullable type
     * array into the OAS 3.0 {@code nullable: true} form.
     *
     * @param schema  The schema to inspect; may be {@code null}.
     * @param visited Identity-based set of already-processed schemas.
     * @param logger  A logger instance for logging messages.
     */
    private void normalizeSchema(@Nullable Schema<?> schema, Set<Schema<?>> visited, Logger logger) {
        if (schema == null || !visited.add(schema)) {
            return;
        }

        Set<String> types = schema.getTypes();
        if (types != null && types.contains(NULL_TYPE)) {
            Set<String> nonNullTypes = new LinkedHashSet<>(types);
            nonNullTypes.remove(NULL_TYPE);

            schema.setNullable(true);

            if (nonNullTypes.size() == 1) {
                String singleType = nonNullTypes.iterator().next();
                schema.setType(singleType);
                schema.setTypes(null);
                logger.debug(
                    "Normalized OAS 3.1 nullable type [{}, null] to nullable:true / type:{} on schema '{}'.",
                    singleType, singleType, schema.getName()
                );
            } else if (nonNullTypes.isEmpty()) {
                schema.setTypes(null);
                logger.debug(
                    "Schema '{}' had types containing only 'null'; cleared types and set nullable:true.",
                    schema.getName()
                );
            } else {
                schema.setTypes(new HashSet<>(nonNullTypes));
                logger.debug(
                    "Removed 'null' from union types {} on schema '{}' and set nullable:true.",
                    nonNullTypes, schema.getName()
                );
            }
        }

        if (schema.getProperties() != null) {
            for (Object value : schema.getProperties().values()) {
                if (value instanceof Schema) {
                    normalizeSchema((Schema<?>) value, visited, logger);
                }
            }
        }

        if (schema.getItems() != null) {
            normalizeSchema(schema.getItems(), visited, logger);
        }

        Object additionalProperties = schema.getAdditionalProperties();
        if (additionalProperties instanceof Schema) {
            normalizeSchema((Schema<?>) additionalProperties, visited, logger);
        }

        if (schema.getAllOf() != null) {
            for (Object member : schema.getAllOf()) {
                if (member instanceof Schema) {
                    normalizeSchema((Schema<?>) member, visited, logger);
                }
            }
        }
        if (schema.getAnyOf() != null) {
            for (Object member : schema.getAnyOf()) {
                if (member instanceof Schema) {
                    normalizeSchema((Schema<?>) member, visited, logger);
                }
            }
        }
        if (schema.getOneOf() != null) {
            for (Object member : schema.getOneOf()) {
                if (member instanceof Schema) {
                    normalizeSchema((Schema<?>) member, visited, logger);
                }
            }
        }

        if (schema.getNot() != null) {
            normalizeSchema(schema.getNot(), visited, logger);
        }
    }
}
