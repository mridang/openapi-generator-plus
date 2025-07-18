package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Defines the contract for a custom OpenAPI normalization rule.
 * Each rule should implement this interface to provide a specific
 * transformation logic to the OpenAPI specification.
 */
public interface CustomNormalizationRule {

    String RULE_VALUE_KEY = "value";

    /**
     * Applies the specific normalization rule to the given OpenAPI object.
     * Implementations should modify the {@code openAPI} object in place.
     *
     * @param openAPI    The OpenAPI object to be modified.
     * @param ruleConfig A map containing the configuration for this specific rule,
     *                   derived from the overall input rules.
     * @param logger     A logger instance for logging messages within the rule.
     */
    void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger);
}
