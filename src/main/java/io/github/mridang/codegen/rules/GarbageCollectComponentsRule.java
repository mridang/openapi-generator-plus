// file: src/main/java/io/github/mridang/codegen/rules/GarbageCollectComponentsRule.java
package io.github.mridang.codegen.rules;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements a "garbage collection" rule for an OpenAPI specification.
 * This rule runs in two passes:
 * 1. It traverses the entire OpenAPI object to find all active `$ref` values.
 * 2. It then iterates through all the definitions in the `components`
 * section and removes any that are not actively referenced.
 * <p>
 * This should be run *after* other rules that might remove references.
 */
public class GarbageCollectComponentsRule implements CustomNormalizationRule {

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting garbage collection of unused components.");

        if (openAPI.getComponents() == null) {
            logger.info("No components section found. Skipping garbage collection.");
            return;
        }

        // Pass 1: Collect all active $ref values from the entire spec.
        Set<String> activeRefs = new HashSet<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> specAsMap = Json.mapper().convertValue(openAPI, Map.class);
        collectRefs(specAsMap, activeRefs);
        logger.info("Found {} active component references.", activeRefs.size());

        // Pass 2: Remove any component that is not in the active set.
        Components components = openAPI.getComponents();
        int schemasRemoved = removeUnused(components.getSchemas(), "#/components/schemas/", activeRefs, logger);
        int responsesRemoved = removeUnused(components.getResponses(), "#/components/responses/", activeRefs, logger);
        int paramsRemoved = removeUnused(components.getParameters(), "#/components/parameters/", activeRefs, logger);
        int examplesRemoved = removeUnused(components.getExamples(), "#/components/examples/", activeRefs, logger);
        int bodiesRemoved = removeUnused(components.getRequestBodies(), "#/components/requestBodies/", activeRefs, logger);
        int headersRemoved = removeUnused(components.getHeaders(), "#/components/headers/", activeRefs, logger);
        int linksRemoved = removeUnused(components.getLinks(), "#/components/links/", activeRefs, logger);
        int callbacksRemoved = removeUnused(components.getCallbacks(), "#/components/callbacks/", activeRefs, logger);

        int totalRemoved = schemasRemoved + responsesRemoved + paramsRemoved + examplesRemoved +
            bodiesRemoved + headersRemoved + linksRemoved + callbacksRemoved;
        logger.info("Garbage collection complete. Removed {} unused components.", totalRemoved);
    }

    /**
     * Recursively traverses a map/list structure to find all values for the "$ref" key.
     *
     * @param node       The current node (can be a Map, List, or other type).
     * @param activeRefs The set where active $ref strings will be stored.
     */
    @SuppressWarnings("unchecked")
    private void collectRefs(Object node, Set<String> activeRefs) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if ("$ref".equals(entry.getKey()) && entry.getValue() instanceof String) {
                    activeRefs.add((String) entry.getValue());
                } else {
                    collectRefs(entry.getValue(), activeRefs);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<Object>) node) {
                collectRefs(item, activeRefs);
            }
        }
    }

    /**
     * A generic method to iterate through a component map and remove unused entries.
     *
     * @param componentMap The map of components (e.g., schemas, parameters).
     * @param prefix       The $ref prefix for this component type (e.g., "#/components/schemas/").
     * @param activeRefs   The set of all active references in the spec.
     * @param logger       The logger instance.
     * @return The number of components removed.
     */
    private <T> int removeUnused(Map<String, T> componentMap, String prefix, Set<String> activeRefs, Logger logger) {
        if (componentMap == null || componentMap.isEmpty()) {
            return 0;
        }

        int removedCount = 0;
        Iterator<Map.Entry<String, T>> iterator = componentMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, T> entry = iterator.next();
            String refPath = prefix + entry.getKey();
            if (!activeRefs.contains(refPath)) {
                logger.info("Removing unused component: {}", refPath);
                iterator.remove();
                removedCount++;
            }
        }
        return removedCount;
    }
}
