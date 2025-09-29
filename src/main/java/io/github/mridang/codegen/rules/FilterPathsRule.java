package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements a custom rule to filter API paths based on regular expressions.
 * Only paths that match at least one of the provided regex patterns will be kept
 * in the OpenAPI specification. Paths that do not match any pattern are removed.
 * <p>
 * If the OpenAPI object's paths are null or empty, a {@code NoPathsException}
 * is thrown to indicate a critical issue.
 */
public class FilterPathsRule implements CustomNormalizationRule {

    /**
     * Applies the rule to filter API paths based on regular expressions.
     *
     * @param openAPI    The OpenAPI object to be modified.
     * @param ruleConfig A map containing the configuration for this rule.
     *                   It expects a key defined by {@code RULE_VALUE_KEY}
     *                   whose value is a comma-separated string of regex patterns.
     * @param logger     A logger instance for logging messages.
     * @throws NoPathsException If the OpenAPI object's paths are null or empty,
     *                          preventing filtering.
     * @throws RuntimeException If no regex patterns are provided in the rule config.
     */
    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        String regexPatternsString = ruleConfig.get(RULE_VALUE_KEY);
        if (regexPatternsString == null || regexPatternsString.isEmpty()) {
            logger.warn("FILTER_PATHS rule enabled, but no regex value provided. Skipping.");
            return;
        }

        List<String> regexPatterns = Arrays.asList(regexPatternsString.split(","));

        logger.info("Starting FILTER_PATHS rule. Patterns: {}", regexPatterns);

        Paths originalPaths = openAPI.getPaths();
        if (originalPaths == null || originalPaths.isEmpty()) {
            throw new NoPathsException(
                "Error: Paths object is null or empty, cannot perform route regex " +
                    "filtering."
            );
        }

        Paths newPaths = new Paths();
        for (Map.Entry<String, PathItem> entry : originalPaths.entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();

            boolean matchesAnyRegex = false;
            for (String regex : regexPatterns) {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(path);
                if (matcher.find()) {
                    matchesAnyRegex = true;
                    break;
                }
            }

            if (matchesAnyRegex) {
                newPaths.addPathItem(path, pathItem);
                logger.debug("Keeping path '{}' as it matches a regex pattern.", path);
            } else {
                logger.info("Removing path '{}' as it does not match any regex pattern.", path);
            }
        }

        openAPI.setPaths(newPaths);
        logger.info("FILTER_PATHS rule completed.");
    }

    /**
     * Custom exception thrown when the OpenAPI specification contains no paths.
     * This exception is re-declared here for self-containment of the rule.
     */
    public static class NoPathsException extends RuntimeException {
        private static final long serialVersionUID = 4242125L;

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
