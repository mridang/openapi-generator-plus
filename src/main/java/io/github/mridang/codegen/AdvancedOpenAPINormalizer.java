package io.github.mridang.codegen;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.rules.CleanEmptyRequestBodiesRule;
import io.github.mridang.codegen.rules.FilterPathsRule;
import io.github.mridang.codegen.rules.OnlyAllowJsonRule;
import io.github.mridang.codegen.rules.TagCompositionMembersRule;
import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.OpenAPINormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom OpenAPI Normalizer that extends the default OpenAPINormalizer
 * to apply specific transformations to the OpenAPI specification.
 * This class orchestrates the application of various custom normalization
 * rules based on the input configuration.
 * <p>
 * Rules are dynamically applied if their corresponding key is found in the
 * {@code inputRules} map provided to the constructor.
 * <p>
 * This normalizer is typically integrated by a custom
 * {@code CodegenConfig} (generator) that explicitly instantiates and
 * invokes its {@code normalize()} method.
 */
public class AdvancedOpenAPINormalizer extends OpenAPINormalizer {

    private static final String RULE_CLEAN_EMPTY_REQUEST_BODIES =
        "CLEAN_EMPTY_REQUEST_BODIES";
    private static final String RULE_ONLY_ALLOW_JSON =
        "ONLY_ALLOW_JSON";
    private static final String RULE_FILTER_PATHS =
        "FILTER_PATHS";
    protected final Logger LOGGER = LoggerFactory.getLogger(
        AdvancedOpenAPINormalizer.class
    );
    private final Map<String, String> inputRules;
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    private final OpenAPI openAPI;

    /**
     * Constructs a new AdvancedOpenAPINormalizer.
     * Initializes the normalizer with the OpenAPI object and any input rules.
     * The constructor calls the superclass constructor to set up the basic
     * normalization environment.
     * <p>
     * Note: Rules are now primarily applied in the {@code normalize()} method
     * after {@code super.normalize()} to ensure all OpenAPI references are
     * resolved before custom logic is applied.
     *
     * @param openAPI    The OpenAPI object representing the API specification
     *                   to be normalized.
     * @param inputRules A map of configuration rules passed to this normalizer.
     *                   These rules control which custom normalizations are applied.
     */
    public AdvancedOpenAPINormalizer(OpenAPI openAPI, Map<String, String> inputRules) {
        super(openAPI, inputRules);
        this.openAPI = openAPI;
        this.inputRules = new HashMap<>(inputRules);
        LOGGER.info("Instance created. Rules: {}", inputRules);


        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        ch.qos.logback.classic.Logger advancedNormalizerLogger =
            loggerContext.getLogger("io.github.mridang.codegen.AdvancedOpenAPINormalizer");
        ch.qos.logback.classic.Logger rulesLogger =
            loggerContext.getLogger("io.github.mridang.codegen.rules");

        advancedNormalizerLogger.setLevel(Level.DEBUG);
        rulesLogger.setLevel(Level.DEBUG);

        advancedNormalizerLogger.setAdditive(false);
        rulesLogger.setAdditive(false);

        advancedNormalizerLogger.addAppender(consoleAppender);
        rulesLogger.addAppender(consoleAppender);
    }

    /**
     * Overrides the main normalization method of the {@code OpenAPINormalizer}
     * base class. This method serves as the primary entry point for all
     * normalization logic, orchestrating both the standard base normalizations
     * and any custom rules implemented within this class.
     * <p>
     * It is crucial to call {@code super.normalize()} first to ensure that
     * the OpenAPI object is fully parsed, all its references are resolved,
     * and its structure is consistent before custom transformations are applied.
     * Failing to do so may lead to {@code NullPointerException} or incorrect
     * behavior in custom rules that rely on a fully processed specification.
     */
    @SuppressWarnings("unused")
    protected void normalize() {
        new TagCompositionMembersRule().apply(openAPI, inputRules, LOGGER);

        if (inputRules.containsKey(RULE_CLEAN_EMPTY_REQUEST_BODIES)) {
            LOGGER.info("Executing rule: {}", RULE_CLEAN_EMPTY_REQUEST_BODIES);
            new CleanEmptyRequestBodiesRule().apply(openAPI, inputRules, LOGGER);
        }

        if (inputRules.containsKey(RULE_ONLY_ALLOW_JSON)) {
            LOGGER.info("Executing rule: {}", RULE_ONLY_ALLOW_JSON);
            new OnlyAllowJsonRule().apply(openAPI, inputRules, LOGGER);
        }

        if (inputRules.containsKey(RULE_FILTER_PATHS)) {
            String regexValue = inputRules.get(RULE_FILTER_PATHS);
            if (regexValue == null || regexValue.isEmpty()) {
                LOGGER.warn(
                    "FILTER_PATHS rule enabled, but no regex value provided. " +
                        "Skipping regex filtering."
                );
            } else {
                List<String> regexPatterns = Arrays.asList(regexValue.split(","));
                LOGGER.info("Applying FILTER_PATHS rule with patterns: {}",
                    regexPatterns);
                new FilterPathsRule().apply(openAPI, inputRules, LOGGER);
                LOGGER.info("FILTER_PATHS rule completed.");
            }
        }

        LOGGER.info("All custom normalizations applied.");
    }
}
