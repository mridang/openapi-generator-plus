package io.github.mridang.codegen;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.rules.*;
import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.OpenAPINormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A custom normalizer that applies a series of modular transformations to an
 * OpenAPI specification based on provided configuration rules.
 * <p>
 * This class orchestrates the execution of custom rules after the default
 * normalization logic has completed, ensuring a consistent and predictable
 * transformation pipeline.
 */
public class AdvancedOpenAPINormalizer extends OpenAPINormalizer {

    private static final String RULE_STRIP_PARAMS = "STRIP_PARAMS";
    private static final String RULE_CLEAN_EMPTY_REQUEST_BODIES = "CLEAN_EMPTY_REQUEST_BODIES";
    private static final String RULE_ONLY_ALLOW_JSON = "ONLY_ALLOW_JSON";
    private static final String RULE_FILTER_PATHS = "FILTER_PATHS";
    private static final String RULE_GARBAGE_COLLECT = "GARBAGE_COLLECT_COMPONENTS";
    @SuppressWarnings("HidingField")
    @SuppressFBWarnings("MF_CLASS_MASKS_FIELD")
    protected final Logger LOGGER = LoggerFactory.getLogger(AdvancedOpenAPINormalizer.class);
    private final Map<String, String> customRules;

    /**
     * Constructs a new AdvancedOpenAPINormalizer.
     *
     * @param openAPI    The OpenAPI object to be normalized.
     * @param inputRules A map of configuration rules that control which custom
     *                   normalizations are applied.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public AdvancedOpenAPINormalizer(OpenAPI openAPI, Map<String, String> inputRules) {
        super(openAPI, inputRules);
        this.customRules = inputRules;
        LOGGER.info("AdvancedOpenAPINormalizer instance created. Rules: {}", customRules);
    }

    /**
     * Overrides the main normalization method to orchestrate a pipeline of
     * default and custom normalization rules.
     */
    @Override
    protected void normalize() {
        super.normalize();
        LOGGER.info("Default normalization complete. Applying custom rules...");

        applyRule(RULE_STRIP_PARAMS, new StripParametersRule());
        applyRule(RULE_CLEAN_EMPTY_REQUEST_BODIES, new CleanEmptyRequestBodiesRule());
        applyRule(RULE_ONLY_ALLOW_JSON, new OnlyAllowJsonRule());
        applyRule(RULE_FILTER_PATHS, new FilterPathsRule());
        applyRule(RULE_GARBAGE_COLLECT, new GarbageCollectComponentsRule());

        LOGGER.info("All custom normalizations applied.");
    }

    /**
     * Applies a given rule if its corresponding key is present in the
     * configuration. This helper method standardizes how each rule is
     * checked and executed.
     *
     * @param ruleKey The configuration key for the rule.
     * @param rule    The instance of the rule to apply.
     */
    private void applyRule(String ruleKey, CustomNormalizationRule rule) {
        if (customRules.containsKey(ruleKey)) {
            LOGGER.info("Executing rule: {}", ruleKey);
            Map<String, String> ruleConfig = Map.of("value", customRules.get(ruleKey));
            rule.apply(this.openAPI, ruleConfig, LOGGER);
        }
    }
}
