package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Implements a custom rule to remove empty request bodies from API operations.
 * An empty request body is defined as one that is null, has no content types,
 * or has content types where all associated schemas are null.
 * <p>
 * This rule iterates through all paths and their operations. For each
 * operation, it inspects the {@code RequestBody}. If the
 * {@code RequestBody} is determined to be empty based on its content
 * and schema definitions, it is set to {@code null} for that operation.
 */
public class CleanEmptyRequestBodiesRule implements CustomNormalizationRule {

    /**
     * Applies the rule to remove empty request bodies from the OpenAPI object.
     *
     * @param openAPI    The OpenAPI object to be modified.
     * @param ruleConfig Configuration specific to this rule (not directly used here,
     *                   but part of the interface).
     * @param logger     A logger instance for logging messages.
     */
    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting CLEAN_EMPTY_REQUEST_BODIES rule.");

        Paths paths = openAPI.getPaths();
        if (paths == null || paths.isEmpty()) {
            throw new NoPathsException(
                "Error: Paths object is null or empty, cannot process request " +
                    "bodies for empty request body removal."
            );
        }

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            PathItem pathItem = pathEntry.getValue();

            for (Operation operation : pathItem.readOperations()) {
                RequestBody requestBody = operation.getRequestBody();

                if (requestBody != null) {
                    boolean hasMeaningfulContent = false;
                    Content content = requestBody.getContent();

                    if (content != null && !content.isEmpty()) {
                        for (MediaType mediaType : content.values()) {
                            Schema<?> schema = mediaType.getSchema();

                            if (schema != null) {
                                hasMeaningfulContent = true;
                                break;
                            }
                        }
                    }

                    if (!hasMeaningfulContent) {
                        logger.info(
                            "Removing empty request body for operation '{}' " +
                                "(Path: {}).", operation.getOperationId(),
                            pathEntry.getKey()
                        );
                        operation.setRequestBody(null);
                    } else {
                        logger.info(
                            "Keeping non-empty request body for operation '{}' " +
                                "(Path: {}).", operation.getOperationId(),
                            pathEntry.getKey()
                        );
                    }
                }
            }
        }
        logger.info("CLEAN_EMPTY_REQUEST_BODIES rule completed.");
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
