// file: src/main/java/io/github/mridang/codegen/rules/StripParametersRule.java
package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements a custom rule to remove specified parameters from all API
 * operations. The parameters to be removed are identified by a case-insensitive,
 * comma-separated list of names provided in the rule's configuration.
 * <p>
 * This rule handles both inline parameter definitions and parameters
 * referenced from the components section (e.g., using $ref). It will remove
 * the parameter from the operation but does not delete the shared component
 * from the global components list.
 */
public class StripParametersRule implements CustomNormalizationRule {

    public static final String RULE_VALUE_KEY = "value";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        String paramsToRemoveString = ruleConfig.get(RULE_VALUE_KEY);
        if (paramsToRemoveString == null || paramsToRemoveString.isEmpty()) {
            logger.warn("STRIP_PARAMS rule enabled, but no value provided. Skipping.");
            return;
        }

        List<String> paramsToRemove = Arrays.stream(paramsToRemoveString.split("\\|"))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toList());

        logger.info("Starting STRIP_PARAMS rule. Will remove: {}", paramsToRemove);

        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new NoPathsException(
                "Error: Paths object is null or empty, cannot strip parameters."
            );
        }


        for (PathItem pathItem : openAPI.getPaths().values()) {
            for (Operation operation : pathItem.readOperations()) {
                if (operation.getParameters() == null) {
                    continue;
                }

                Iterator<Parameter> iterator = operation.getParameters().iterator();
                while (iterator.hasNext()) {
                    Parameter parameter = iterator.next();
                    String paramName = null;

                    // New logic to handle both inline and $ref parameters
                    if (parameter.get$ref() != null) {
                        // For a $ref like "#/components/parameters/MyParam",
                        // extract "MyParam".
                        String ref = parameter.get$ref();
                        paramName = ref.substring(ref.lastIndexOf('/') + 1);
                    } else {
                        // Existing logic for inline parameters
                        paramName = parameter.getName();
                    }

                    if (paramName != null && paramsToRemove.contains(paramName.toLowerCase(Locale.ENGLISH))) {
                        logger.info("Removing parameter '{}' from operation '{}'",
                            paramName, operation.getOperationId());
                        iterator.remove();
                    }
                }
            }
        }
        logger.info("STRIP_PARAMS rule completed.");
    }

    public static class NoPathsException extends RuntimeException {
        private static final long serialVersionUID = 781561L;

        public NoPathsException(String message) {
            super(message);
        }
    }
}
