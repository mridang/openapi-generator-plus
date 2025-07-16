package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds schemas that mix 'properties' and 'oneOf' and flattens the properties
 * from the 'oneOf' choices into the main properties block. This makes the schema
 * unambiguous for all code generators.
 */
public class FlattenCompositionRule implements CustomNormalizationRule {

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return;
        }
        logger.info("Starting rule to flatten mixed composition schemas.");

        List<String> schemasToProcess = new ArrayList<>(openAPI.getComponents().getSchemas().keySet());

        for (String schemaName : schemasToProcess) {
            Schema<?> model = openAPI.getComponents().getSchemas().get(schemaName);
            if (model == null) {
                continue;
            }

            boolean hasProperties = model.getProperties() != null && !model.getProperties().isEmpty();
            boolean hasOneOf = model.getOneOf() != null && !model.getOneOf().isEmpty();

            if (hasProperties && hasOneOf) {
                logger.info("Flattening mixed schema: '{}'", schemaName);

                for (Schema<?> oneOfSchema : model.getOneOf()) {
                    Schema<?> resolvedOneOfSchema = oneOfSchema;
                    if (oneOfSchema.get$ref() != null) {
                        String ref = oneOfSchema.get$ref();
                        String componentName = ref.substring(ref.lastIndexOf('/') + 1);
                        resolvedOneOfSchema = openAPI.getComponents().getSchemas().get(componentName);
                    }

                    if (resolvedOneOfSchema != null && resolvedOneOfSchema.getProperties() != null) {
                        for (Map.Entry<String, Schema> entry : resolvedOneOfSchema.getProperties().entrySet()) {
                            if (!model.getProperties().containsKey(entry.getKey())) {
                                model.addProperties(entry.getKey(), entry.getValue());
                                logger.info("  - Flattened property '{}' into schema '{}'", entry.getKey(), schemaName);
                            }
                        }
                    }
                }
                // Remove the original oneOf block to prevent generator confusion
                model.setOneOf(null);
                logger.info("  - Cleared the oneOf block for schema '{}'.", schemaName);
            }
        }
    }
}
