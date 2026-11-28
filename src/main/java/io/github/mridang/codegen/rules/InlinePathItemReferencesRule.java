package io.github.mridang.codegen.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Inlines a path whose item is a {@code $ref} (into {@code #/components/pathItems/...} or
 * {@code #/paths/...}) into the referencing path: operations, parameters and servers come from
 * the target and the referencing item's own fields win. An {@code operationId} already used by
 * an earlier path is dropped from the copy, so the stock generated id applies and the document
 * keeps unique ids; a warning says so.
 */
public class InlinePathItemReferencesRule implements CustomNormalizationRule {

    private static final String PATH_ITEMS = "#/components/pathItems/";
    private static final String PATHS = "#/paths/";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getPaths() == null) {
            return;
        }
        final Set<String> ids = new HashSet<>();
        for (PathItem item : openAPI.getPaths().values()) {
            if (item != null && item.get$ref() == null) {
                collectIds(item, ids);
            }
        }
        for (Map.Entry<String, PathItem> entry : new ArrayList<>(openAPI.getPaths().entrySet())) {
            final PathItem item = entry.getValue();
            if (item == null || item.get$ref() == null) {
                continue;
            }
            final PathItem target = resolve(openAPI, item.get$ref());
            if (target == null) {
                logger.warn("Path {} references {}, which is not a path item; it is skipped.",
                        entry.getKey(), item.get$ref());
                continue;
            }
            final PathItem copy = copy(target, logger);
            if (copy == null) {
                continue;
            }
            for (Operation operation : copy.readOperations()) {
                final String id = operation.getOperationId();
                if (id != null && !ids.add(id)) {
                    logger.warn("Path {} reuses operationId {}; the copy takes the generated id.",
                            entry.getKey(), id);
                    operation.setOperationId(null);
                }
            }
            merge(item, copy);
            for (Operation operation : item.readOperations()) {
                if (operation.getSummary() == null) {
                    operation.setSummary(item.getSummary());
                }
                if (operation.getDescription() == null) {
                    operation.setDescription(item.getDescription());
                }
            }
            item.set$ref(null);
        }
    }

    private static void collectIds(PathItem item, Set<String> ids) {
        for (Operation operation : item.readOperations()) {
            if (operation.getOperationId() != null) {
                ids.add(operation.getOperationId());
            }
        }
    }

    @Nullable
    private static PathItem resolve(OpenAPI openAPI, String ref) {
        if (ref.startsWith(PATH_ITEMS)) {
            final String name = ref.substring(PATH_ITEMS.length());
            return openAPI.getComponents() == null || openAPI.getComponents().getPathItems() == null
                    ? null
                    : openAPI.getComponents().getPathItems().get(name);
        }
        if (ref.startsWith(PATHS) && openAPI.getPaths() != null) {
            final String path = ref.substring(PATHS.length()).replace("~1", "/").replace("~0", "~");
            final PathItem target = openAPI.getPaths().get("/" + path.replaceFirst("^/", ""));
            return target == null || target.get$ref() != null ? null : target;
        }
        return null;
    }

    @Nullable
    private static PathItem copy(PathItem source, Logger logger) {
        try {
            final ObjectMapper mapper = Json31.mapper();
            return mapper.readValue(mapper.writeValueAsString(source), PathItem.class);
        } catch (java.io.IOException e) {
            logger.warn("A referenced path item could not be copied: {}", e.getClass().getName());
            return null;
        }
    }

    private static void merge(PathItem into, PathItem from) {
        if (into.getSummary() == null) {
            into.setSummary(from.getSummary());
        }
        if (into.getDescription() == null) {
            into.setDescription(from.getDescription());
        }
        if (into.getParameters() == null) {
            into.setParameters(from.getParameters());
        }
        if (into.getServers() == null) {
            into.setServers(from.getServers());
        }
        if (into.getGet() == null) {
            into.setGet(from.getGet());
        }
        if (into.getPut() == null) {
            into.setPut(from.getPut());
        }
        if (into.getPost() == null) {
            into.setPost(from.getPost());
        }
        if (into.getDelete() == null) {
            into.setDelete(from.getDelete());
        }
        if (into.getOptions() == null) {
            into.setOptions(from.getOptions());
        }
        if (into.getHead() == null) {
            into.setHead(from.getHead());
        }
        if (into.getPatch() == null) {
            into.setPatch(from.getPatch());
        }
        if (into.getTrace() == null) {
            into.setTrace(from.getTrace());
        }
    }
}
