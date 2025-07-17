package io.github.mridang.codegen.generators;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;

public interface UnsupportedFeaturesValidator {

    /**
     * Checks an operation for unsupported features like cookies, form parameters,
     * and file uploads. Throws a RuntimeException if an unsupported feature is
     * found, halting the generation process.
     *
     * @param operation The OpenAPI Operation object to validate.
     */
    @SuppressFBWarnings({"IMPROPER_UNICODE", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION"})
    default void validateOperation(Operation operation) {
        // Check for cookie parameters
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("cookie".equalsIgnoreCase(param.getIn())) {
                    throw new RuntimeException(
                        "Operation '" + operation.getOperationId() + "' uses cookie parameters, which are not supported."
                    );
                }
            }
        }

        // Check for form parameters or file uploads
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            for (String mediaType : operation.getRequestBody().getContent().keySet()) {
                if ("application/x-www-form-urlencoded".equalsIgnoreCase(mediaType) || "multipart/form-data".equalsIgnoreCase(mediaType)) {
                    throw new RuntimeException(
                        "Operation '" + operation.getOperationId() + "' uses form parameters or file uploads ('" + mediaType + "'), which are not supported."
                    );
                }
            }
        }
    }
}
