package io.github.mridang.codegen.rules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implements a custom rule to enforce "application/json" as the only allowed
 * content type within request bodies and responses in the OpenAPI specification.
 * <p>
 * This rule first filters out all content types other than "application/json".
 * After filtering, it performs a validation step, throwing a {@code RuntimeException}
 * if any content type that is not "application/json" is still found,
 * indicating a failure in the filtering or an unexpected spec structure.
 */
public class OnlyAllowJsonRule implements CustomNormalizationRule {

    /**
     * Applies the rule to enforce "application/json" content type and validate.
     *
     * @param openAPI    The OpenAPI object to be modified.
     * @param ruleConfig Configuration specific to this rule (not directly used here,
     *                   but part of the interface).
     * @param logger     A logger instance for logging messages.
     */
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
        "application/json",
        "text/plain",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
        "application/octet-stream"
    );

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting ONLY_ALLOW_JSON rule. Allowed types: {}", ALLOWED_TYPES);

        // Step 1: Filter out unsupported content types.
        filterContentType(openAPI, logger);

        // Step 2: Validate that no unsupported content types remain after filtering.
        validateAllowedTypesOnly(openAPI, logger);

        logger.info("ONLY_ALLOW_JSON rule completed.");
    }

    private static boolean isAllowedContentType(String contentType) {
        return ALLOWED_TYPES.contains(contentType) || contentType.endsWith("+json");
    }

    /**
     * Filters the OpenAPI spec to keep only supported content types in request
     * bodies and responses.
     *
     * @param openAPI The OpenAPI object to be modified.
     * @param logger  A logger instance for logging messages.
     */
    private void filterContentType(OpenAPI openAPI, Logger logger) {
        Paths paths = openAPI.getPaths();
        if (paths == null || paths.isEmpty()) {
            logger.info("No paths found for content type filtering.");
            return;
        }

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            for (Operation operation : pathItem.readOperations()) {
                // Filter RequestBody content
                RequestBody requestBody = operation.getRequestBody();
                if (requestBody != null && requestBody.getContent() != null) {
                    Content content = requestBody.getContent();
                    Iterator<Map.Entry<String, MediaType>> contentIterator =
                        content.entrySet().iterator();
                    while (contentIterator.hasNext()) {
                        Map.Entry<String, MediaType> entry =
                            contentIterator.next();
                        String contentType = entry.getKey();
                        if (!isAllowedContentType(contentType)) {
                            logger.warn(
                                "Content type '{}' in request body for operation " +
                                    "'{}' (Path: {}) isn't allowed. Removing it.",
                                contentType, operation.getOperationId(), path
                            );
                            contentIterator.remove();
                        }
                    }
                    if (content.isEmpty()) {
                        operation.setRequestBody(null);
                        logger.info(
                            "Request body for operation '{}' (Path: {}) became " +
                                "empty after filtering and was removed.",
                            operation.getOperationId(), path
                        );
                    }
                }

                // Filter Responses content
                if (operation.getResponses() != null) {
                    for (Map.Entry<String, ApiResponse> responseEntry :
                        operation.getResponses().entrySet()) {
                        ApiResponse apiResponse = responseEntry.getValue();
                        if (apiResponse != null && apiResponse.getContent() != null) {
                            Content content = apiResponse.getContent();
                            Iterator<Map.Entry<String, MediaType>> contentIterator =
                                content.entrySet().iterator();
                            while (contentIterator.hasNext()) {
                                Map.Entry<String, MediaType> entry =
                                    contentIterator.next();
                                String contentType = entry.getKey();
                                if (!isAllowedContentType(contentType)) {
                                    logger.warn(
                                        "Content type '{}' in response for operation " +
                                            "'{}' (Path: {}) - Status: {} isn't " +
                                            "allowed. Removing it.",
                                        contentType, operation.getOperationId(), path,
                                        responseEntry.getKey()
                                    );
                                    contentIterator.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
        logger.info("Content type filtering completed.");
    }

    /**
     * Validates that no unsupported content types remain in the OpenAPI
     * specification's request bodies or responses after filtering.
     *
     * @param openAPI The OpenAPI object to validate.
     * @param logger  A logger instance for logging messages.
     * @throws RuntimeException If any unsupported content type is found.
     */
    @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
    private void validateAllowedTypesOnly(OpenAPI openAPI, Logger logger) {
        Paths paths = openAPI.getPaths();
        if (paths == null || paths.isEmpty()) {
            throw new NoPathsException(
                "Error: Paths object is null or empty, cannot perform content " +
                    "type validation."
            );
        }

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            for (Operation operation : pathItem.readOperations()) {
                // Check RequestBody content
                RequestBody requestBody = operation.getRequestBody();
                if (requestBody != null && requestBody.getContent() != null) {
                    for (String contentType : requestBody.getContent().keySet()) {
                        if (!isAllowedContentType(contentType)) {
                            logger.error(
                                "Validation Error: Unsupported content type '{}' " +
                                    "found in request body for operation '{}' " +
                                    "(Path: {}) after filtering.",
                                contentType, operation.getOperationId(), path
                            );
                            throw new RuntimeException(
                                "Validation Error: Unsupported content type '" +
                                    contentType + "' found in request body " +
                                    "for path '" + path + "' [" +
                                    operation.getOperationId() + "] after filtering."
                            );
                        }
                    }
                }

                // Check Responses content
                if (operation.getResponses() != null) {
                    for (Map.Entry<String, ApiResponse> responseEntry :
                        operation.getResponses().entrySet()) {
                        ApiResponse apiResponse = responseEntry.getValue();
                        if (apiResponse != null && apiResponse.getContent() != null) {
                            for (String contentType : apiResponse.getContent().keySet()) {
                                if (!isAllowedContentType(contentType)) {
                                    logger.error(
                                        "Validation Error: Unsupported content type '{}' " +
                                            "found in response for operation '{}' " +
                                            "(Path: {}) - Status: {} after filtering.",
                                        contentType, operation.getOperationId(), path,
                                        responseEntry.getKey()
                                    );
                                    throw new RuntimeException(
                                        "Validation Error: Unsupported content type '" +
                                            contentType + "' found in response " +
                                            "for path '" + path + "' [" +
                                            operation.getOperationId() + "] - Status: " +
                                            responseEntry.getKey() + " after filtering."
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Custom exception thrown when the OpenAPI specification contains no paths.
     * This exception is re-declared here for self-containment of the rule.
     */
    public static class NoPathsException extends RuntimeException {
        private static final long serialVersionUID = 4242525L;

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
