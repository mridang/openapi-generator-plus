package io.github.mridang.codegen;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.rules.*;
import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.OpenAPINormalizer;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Iterator;
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
    private static final String RULE_SCRIPTABLE = "RUN_SCRIPT";
    @Nullable
    private final Logger customLogger;
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

        org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(AdvancedOpenAPINormalizer.class);

        if (slf4jLogger instanceof Logger) {
            this.customLogger = (Logger) slf4jLogger;
            Logger parentLogger = (Logger) LoggerFactory.getLogger("org.openapitools");
            Iterator<Appender<ILoggingEvent>> appenderIterator = parentLogger.iteratorForAppenders();
            while (appenderIterator.hasNext()) {
                Appender<ILoggingEvent> appender = appenderIterator.next();
                customLogger.addAppender(appender);
            }
            if (parentLogger.getLevel() != null) {
                customLogger.setLevel(parentLogger.getLevel());
            }
            customLogger.setAdditive(false);
        } else {
            this.customLogger = null;
        }

        getLogger().info("AdvancedOpenAPINormalizer instance created. Rules: {}", customRules);
    }

    /**
     * Returns the appropriate logger instance based on runtime environment.
     *
     * @return Logger instance
     */
    private org.slf4j.Logger getLogger() {
        return customLogger != null ? customLogger : LOGGER;
    }

    /**
     * Overrides the main normalization method to orchestrate a pipeline of
     * default and custom normalization rules.
     */
    @Override
    protected void normalize() {
        super.normalize();
        getLogger().info("Default normalization complete. Applying custom rules...");

        applyRule(RULE_STRIP_PARAMS, new StripParametersRule());
        applyRule(RULE_CLEAN_EMPTY_REQUEST_BODIES, new CleanEmptyRequestBodiesRule());
        applyRule(RULE_ONLY_ALLOW_JSON, new OnlyAllowJsonRule());
        applyRule(RULE_FILTER_PATHS, new FilterPathsRule());
        applyRule(RULE_SCRIPTABLE, new ScriptableRule());
        applyRule(RULE_GARBAGE_COLLECT, new GarbageCollectComponentsRule());

        getLogger().info("All custom normalizations applied.");
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
            getLogger().info("Executing rule: {}", ruleKey);
            Map<String, String> ruleConfig = Map.of("value", customRules.get(ruleKey));
            rule.apply(this.openAPI, ruleConfig, getLogger());
        }
    }
}
