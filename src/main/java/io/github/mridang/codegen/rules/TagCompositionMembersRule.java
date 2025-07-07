package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds schemas that mix 'properties' and 'oneOf', flattens the properties
 * from the 'oneOf' choices into the main properties block, and tags them
 * with a vendor extension.
 */
public class TagCompositionMembersRule implements CustomNormalizationRule {

    @Override
    @SuppressWarnings("unchecked")
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return;
        }
        logger.info("Starting rule to flatten and tag 'oneOf' properties.");

        List<String> schemasToProcess = new ArrayList<>(openAPI.getComponents().getSchemas().keySet());

        for (String schemaName : schemasToProcess) {
            Schema<?> model = openAPI.getComponents().getSchemas().get(schemaName);

            if (model == null) {
                continue;
            }

            boolean hasProperties = model.getProperties() != null && !model.getProperties().isEmpty();
            boolean hasOneOf = model.getOneOf() != null && !model.getOneOf().isEmpty();

            if (hasOneOf) {
                logger.info("Processing mixed schema: '{}'", schemaName);

                if (model.getProperties() == null) {
                    model.setProperties(new LinkedHashMap<>());
                }

                for (Schema<?> oneOfSchema : model.getOneOf()) {
                    Schema<?> resolvedOneOfSchema = oneOfSchema;
                    if (oneOfSchema.get$ref() != null) {
                        String ref = oneOfSchema.get$ref();
                        String componentName = ref.substring(ref.lastIndexOf('/') + 1);
                        resolvedOneOfSchema = openAPI.getComponents().getSchemas().get(componentName);
                    }

                    if (resolvedOneOfSchema != null && resolvedOneOfSchema.getProperties() != null) {
                        for (Map.Entry<String, Schema> entry : resolvedOneOfSchema.getProperties().entrySet()) {
                            String propertyName = entry.getKey();
                            Schema<?> propertySchema = entry.getValue();

                            // THIS IS THE MISSING CHECK (#3 from previous explanations)
                            if (!model.getProperties().containsKey(propertyName)) { // Add this 'if' condition
                                model.addProperties(propertyName, propertySchema);
                                Schema<?> mainProperty = (Schema<?>) model.getProperties().get(propertyName);

                                // FIX: Add a null check before using the property
                                if (mainProperty != null) {
                                    mainProperty.addExtension("x-oneof-member", true);
                                    logger.info("  - Flattened and TAGGED property '{}' in schema '{}'", propertyName, schemaName);
                                } else {
                                    // It's good to log if it failed to retrieve for tagging, even if not null.
                                    logger.warn("  - Failed to retrieve flattened property '{}' for tagging in schema '{}'", propertyName, schemaName);
                                }
                            } else {
                                // Add this else block for clarity and debugging
                                logger.info("  - Property '{}' already exists in schema '{}', skipping flattening (deduplication).", propertyName, schemaName);
                            }
                        }
                    }
                }
                model.setOneOf(null);
                logger.info("  - Cleared the oneOf block for schema '{}' after processing.", schemaName);
            }
        }
    }
}
