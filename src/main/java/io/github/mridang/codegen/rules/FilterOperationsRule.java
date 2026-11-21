package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filters operations by HTTP method or by tag. One instance serves one of the
 * four rules {@code FILTER_METHODS}, {@code EXCLUDE_METHODS}, {@code FILTER_TAGS}
 * and {@code EXCLUDE_TAGS}.
 *
 * <p>The value is a {@code |}-separated, case-insensitive list (the normalizer
 * option string is itself comma-separated, so a comma cannot be used). An
 * include rule keeps only the operations that match; an exclude rule removes the
 * operations that match. For tags an operation matches when any of its tags is
 * listed, so an untagged operation never matches an include rule. A path left
 * with no operation is removed. Because the rules run one after another,
 * an exclude rule always wins over an include rule.
 */
public class FilterOperationsRule implements CustomNormalizationRule {

    /** What the rule matches an operation on. */
    public enum Target {
        METHODS,
        TAGS
    }

    private final String ruleName;
    private final Target target;
    private final boolean exclude;

    /**
     * Creates the rule.
     *
     * @param ruleName the option key, used in log messages
     * @param target   whether to match on HTTP method or on tag
     * @param exclude  true to remove matches, false to keep only matches
     */
    public FilterOperationsRule(String ruleName, Target target, boolean exclude) {
        this.ruleName = ruleName;
        this.target = target;
        this.exclude = exclude;
    }

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        String value = ruleConfig.get(RULE_VALUE_KEY);
        if (value == null || value.isBlank()) {
            logger.warn("{} rule enabled, but no value provided. Skipping.", ruleName);
            return;
        }
        Set<String> wanted = Arrays.stream(value.split("\\|"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        logger.info("Starting {} rule. Values: {}", ruleName, wanted);

        Paths paths = openAPI.getPaths();
        if (paths == null || paths.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, PathItem>> it = paths.entrySet().iterator();
        while (it.hasNext()) {
            PathItem item = it.next().getValue();
            if (item == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> op : item.readOperationsMap().entrySet()) {
                if (matches(op.getKey(), op.getValue(), wanted) == exclude) {
                    item.operation(op.getKey(), null);
                }
            }
            if (item.readOperations().isEmpty()) {
                it.remove();
            }
        }
        logger.info("{} rule completed.", ruleName);
    }

    private boolean matches(PathItem.HttpMethod method, Operation operation, Set<String> wanted) {
        if (target == Target.METHODS) {
            return wanted.contains(method.name().toLowerCase(Locale.ROOT));
        }
        if (operation.getTags() == null) {
            return false;
        }
        return operation.getTags().stream()
            .anyMatch(tag -> wanted.contains(tag.trim().toLowerCase(Locale.ROOT)));
    }
}
