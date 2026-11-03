package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Drops operations (and paths whose every operation is dropped) marked
 * {@code x-internal: true} from the OpenAPI document the SDK generator
 * sees. The full original document is still served by the chasm mock
 * server — only the code generator's view of the spec is pruned.
 *
 * <p>This lets us bake transport-test endpoints (echo, delay,
 * compression, redirects, error statuses, multi-value headers, etc.)
 * straight into the shared {@code petstore/openapi.yaml} for chasm to
 * serve, without polluting every generated client with junk SDK methods
 * like {@code testSlow()} or {@code testEchoBody()}.
 *
 * <p>The rule walks every {@link PathItem}'s GET / PUT / POST / DELETE /
 * OPTIONS / HEAD / PATCH / TRACE operation, removes any whose
 * {@code extensions} contains {@code x-internal} with value
 * {@code true}, and removes the path entirely once every method has
 * been stripped from it.
 */
public class DropInternalOperationsRule implements CustomNormalizationRule {

    /** Vendor-extension key used to mark a test-fixture operation. */
    public static final String X_INTERNAL = "x-internal";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        final Paths paths = openAPI.getPaths();
        if (paths == null || paths.isEmpty()) {
            return;
        }
        int droppedOps = 0;
        int droppedPaths = 0;
        final java.util.Iterator<Map.Entry<String, PathItem>> it = paths.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<String, PathItem> entry = it.next();
            final PathItem pi = entry.getValue();
            if (pi == null) {
                continue;
            }
            if (isInternal(pi.getGet())) { pi.setGet(null); droppedOps++; }
            if (isInternal(pi.getPut())) { pi.setPut(null); droppedOps++; }
            if (isInternal(pi.getPost())) { pi.setPost(null); droppedOps++; }
            if (isInternal(pi.getDelete())) { pi.setDelete(null); droppedOps++; }
            if (isInternal(pi.getOptions())) { pi.setOptions(null); droppedOps++; }
            if (isInternal(pi.getHead())) { pi.setHead(null); droppedOps++; }
            if (isInternal(pi.getPatch())) { pi.setPatch(null); droppedOps++; }
            if (isInternal(pi.getTrace())) { pi.setTrace(null); droppedOps++; }
            if (pi.readOperations().isEmpty()) {
                it.remove();
                droppedPaths++;
            }
        }
        if (droppedOps > 0 || droppedPaths > 0) {
            logger.info(
                    "DROP_INTERNAL rule removed {} operation(s) and {} now-empty path(s)",
                    droppedOps,
                    droppedPaths);
        }
    }

    private static boolean isInternal(Operation op) {
        if (op == null) {
            return false;
        }
        final Map<String, Object> ext = op.getExtensions();
        if (ext == null) {
            return false;
        }
        final Object v = ext.get(X_INTERNAL);
        return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
    }
}
