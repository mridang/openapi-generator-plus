package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Base of the rules that rewrite individual schemas of a document: it visits every schema
 * reachable from the components and the paths exactly once (children before the schema's own
 * rewrite is not guaranteed; a subclass sees each schema once, parents first) and hands it to
 * {@link #visit}.
 */
abstract class SchemaWalkingRule implements CustomNormalizationRule {

    private final Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Rewrites one schema in place; called once per schema, never for a {@code $ref}. */
    protected abstract void visit(Schema<?> schema, Logger logger);

    @Override
    public final void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        visited.clear();
        if (openAPI.getComponents() != null) {
            if (openAPI.getComponents().getSchemas() != null) {
                for (Object schema :
                        new ArrayList<Object>(openAPI.getComponents().getSchemas().values())) {
                    walk((Schema<?>) schema, logger);
                }
            }
            if (openAPI.getComponents().getParameters() != null) {
                for (Parameter parameter : openAPI.getComponents().getParameters().values()) {
                    walkParameter(parameter, logger);
                }
            }
            if (openAPI.getComponents().getRequestBodies() != null) {
                for (RequestBody body : openAPI.getComponents().getRequestBodies().values()) {
                    if (body != null) {
                        walkContent(body.getContent(), logger);
                    }
                }
            }
            if (openAPI.getComponents().getResponses() != null) {
                for (ApiResponse response : openAPI.getComponents().getResponses().values()) {
                    if (response != null) {
                        walkContent(response.getContent(), logger);
                    }
                }
            }
        }
        if (openAPI.getPaths() != null) {
            for (PathItem item : openAPI.getPaths().values()) {
                if (item == null) {
                    continue;
                }
                if (item.getParameters() != null) {
                    for (Parameter parameter : item.getParameters()) {
                        walkParameter(parameter, logger);
                    }
                }
                for (Operation operation : item.readOperations()) {
                    if (operation.getParameters() != null) {
                        for (Parameter parameter : operation.getParameters()) {
                            walkParameter(parameter, logger);
                        }
                    }
                    if (operation.getRequestBody() != null) {
                        walkContent(operation.getRequestBody().getContent(), logger);
                    }
                    if (operation.getResponses() != null) {
                        for (ApiResponse response : operation.getResponses().values()) {
                            if (response != null) {
                                walkContent(response.getContent(), logger);
                            }
                        }
                    }
                }
            }
        }
    }

    private void walkParameter(@Nullable Parameter parameter, Logger logger) {
        if (parameter != null) {
            walk(parameter.getSchema(), logger);
            walkContent(parameter.getContent(), logger);
        }
    }

    private void walkContent(@Nullable Content content, Logger logger) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType != null) {
                walk(mediaType.getSchema(), logger);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void walk(@Nullable Schema schema, Logger logger) {
        if (schema == null || schema.get$ref() != null || !visited.add(schema)) {
            return;
        }
        visit(schema, logger);
        final Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Schema child : new ArrayList<Schema>(properties.values())) {
                walk(child, logger);
            }
        }
        walk(schema.getItems(), logger);
        if (schema.getAdditionalProperties() instanceof Schema) {
            walk((Schema) schema.getAdditionalProperties(), logger);
        }
        walkList(schema.getAllOf(), logger);
        walkList(schema.getAnyOf(), logger);
        walkList(schema.getOneOf(), logger);
    }

    @SuppressWarnings("rawtypes")
    private void walkList(@Nullable List<Schema> list, Logger logger) {
        if (list != null) {
            for (Schema schema : new ArrayList<Schema>(list)) {
                walk(schema, logger);
            }
        }
    }
}
