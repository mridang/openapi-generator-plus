package io.github.mridang.codegen.generators;

import io.swagger.v3.oas.models.Operation;

public interface UnsupportedFeaturesValidator {

    /**
     * Checks an operation for unsupported features. Throws a RuntimeException if
     * an unsupported feature is found, halting the generation process.
     *
     * @param operation The OpenAPI Operation object to validate.
     */
    default void validateOperation(Operation operation) {
        // All previously unsupported features (cookie parameters,
        // application/x-www-form-urlencoded) are now supported.
    }
}
