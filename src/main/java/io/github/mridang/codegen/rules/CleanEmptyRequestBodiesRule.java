package io.github.mridang.codegen.rules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Implements a custom rule to handle empty request bodies in an OpenAPI specification.
 * It operates in two modes, configured via ruleConfig:
 * 1.  <b>Tag (default):</b> Adds an 'x-is-empty-body: true' vendor extension to any
 * request body that is both 'required' and effectively empty.
 * 2.  <b>Remove:</b> Deletes any request body that is both optional (not required)
 * and effectively empty.
 */
public class CleanEmptyRequestBodiesRule implements CustomNormalizationRule {

    public static final String RULE_VALUE_KEY = "value";

    /**
     * Checks if a schema is "effectively empty". An empty schema is one that is
     * null, a broken reference, defines an object with no properties, or is a
     * blank schema with no defining characteristics.
     *
     * @param schema  The schema to inspect, which may be a reference.
     * @param openAPI The full OpenAPI model, used to resolve references.
     * @return true if the schema is effectively empty, false otherwise.
     */
    private boolean isSchemaEffectivelyEmpty(@Nullable Schema<?> schema, OpenAPI openAPI) {
        if (schema == null) {
            return true;
        }

        // If the schema is a reference ($ref), resolve it from the components.
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            // Assumes a simple local reference like '#/components/schemas/MySchema'
            String componentName = ref.substring(ref.lastIndexOf('/') + 1);
            if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                schema = openAPI.getComponents().getSchemas().get(componentName);
                if (schema == null) {
                    return true; // The reference is broken or points to null.
                }
            }
        }

        // An object schema is empty if it has no properties and doesn't allow additional ones.
        if ("object".equals(schema.getType())) {
            boolean hasNoProperties = schema.getProperties() == null || schema.getProperties().isEmpty();
            Object additionalProps = schema.getAdditionalProperties();
            boolean allowsAdditional = additionalProps instanceof Schema || Boolean.TRUE.equals(additionalProps);
            return hasNoProperties && !allowsAdditional;
        }

        // A schema is also empty if it has no type and no other structural keywords.
        // This catches cases like a completely blank `new Schema<>()`.
        return schema.getType() == null &&
            schema.getProperties() == null &&
            schema.getItems() == null &&
            schema.getAllOf() == null &&
            schema.getAnyOf() == null &&
            schema.getOneOf() == null;
    }

    /**
     * Applies the rule to tag or remove empty request bodies from the OpenAPI object.
     * The mode is determined by the 'value' key in ruleConfig, defaulting to "Tag".
     *
     * @param openAPI    The OpenAPI object to be modified.
     * @param ruleConfig Configuration for this rule.
     * @param logger     A logger instance for logging messages.
     */
    @Override
    @SuppressFBWarnings("IMPROPER_UNICODE")
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        // Determine the operating mode, defaulting to "Tag".
        String mode = ruleConfig.getOrDefault(RULE_VALUE_KEY, "Tag");
        logger.info("Starting rule to handle empty request bodies in '{}' mode.", mode);

        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new NoPathsException(
                "Error: Paths object is null or empty, cannot process request " +
                    "bodies for empty request body removal."
            );
        }

        for (PathItem pathItem : openAPI.getPaths().values()) {
            for (Operation operation : pathItem.readOperations()) {
                RequestBody requestBody = operation.getRequestBody();
                if (requestBody == null) {
                    continue;
                }

                Content content = requestBody.getContent();
                Schema<?> schema = null;
                // Get the schema from the first available media type.
                if (content != null && !content.isEmpty()) {
                    MediaType mediaType = content.values().iterator().next();
                    schema = mediaType.getSchema();
                }

                if (isSchemaEffectivelyEmpty(schema, openAPI)) {
                    // In "Remove" mode, only remove the body if it's optional.
                    if ("Remove".equalsIgnoreCase(mode)) {
                        if (!Boolean.TRUE.equals(requestBody.getRequired())) {
                            logger.info("Removing optional empty request body for operation '{}'", operation.getOperationId());
                            operation.setRequestBody(null);
                        }
                    }
                    // In "Tag" mode, only tag the body if it's required.
                    else {
                        if (Boolean.TRUE.equals(requestBody.getRequired())) {
                            logger.info("Tagging required empty request body for operation '{}' with 'x-is-empty-body: true'", operation.getOperationId());
                            requestBody.addExtension("x-is-empty-body", true);
                        }
                    }
                }
            }
        }
        logger.info("Rule for handling empty request bodies completed.");
    }

    /**
     * Custom exception thrown when the OpenAPI specification contains no paths.
     * This exception is re-declared here for self-containment of the rule.
     */
    public static class NoPathsException extends RuntimeException {
        private static final long serialVersionUID = 144114L;

        /**
         * Constructs a new NoPathsException with the specified detail message.
         *
         * @param message The detail message.
         */
        public NoPathsException(String message) {
            super(message);
        }
    }
}
