package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * OpenAPI says that a header parameter named {@code Accept}, {@code Content-Type} or
 * {@code Authorization} (in any case) must be ignored, because those headers belong to the
 * media-type and security objects (decision D115). The SDK keeps control of them: it negotiates
 * the first two and the authenticators write the third. A caller can still pass a per-call
 * header through the call options.
 */
public class IgnoreReservedHeadersRule implements CustomNormalizationRule {

    private static final Set<String> RESERVED = Set.of("accept", "content-type", "authorization");

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getPaths() == null) {
            return;
        }
        for (PathItem item : openAPI.getPaths().values()) {
            if (item == null) {
                continue;
            }
            item.setParameters(strip(openAPI, item.getParameters(), "path item", logger));
            for (Operation operation : item.readOperations()) {
                operation.setParameters(
                        strip(openAPI, operation.getParameters(), operation.getOperationId(), logger));
            }
        }
    }

    /* Returns a filtered copy rather than removing in place: the parsed document can share a
     * parameter list between generator runs, and removing from it while another run reads it
     * throws ConcurrentModificationException. */
    @Nullable private static List<Parameter> strip(
            OpenAPI openAPI, @Nullable List<Parameter> parameters, @Nullable String where, Logger logger) {
        if (parameters == null) {
            return null;
        }
        final List<Parameter> kept = new java.util.ArrayList<>(parameters.size());
        for (Parameter parameter : List.copyOf(parameters)) {
            if (!isReserved(openAPI, parameter, where, logger)) {
                kept.add(parameter);
            }
        }
        return kept;
    }

    private static boolean isReserved(
            OpenAPI openAPI, Parameter parameter, @Nullable String where, Logger logger) {
        final Parameter resolved = resolve(openAPI, parameter);
        if (resolved == null) {
            return false;
        }
        final String name = resolved.getName();
        if (!"header".equals(resolved.getIn()) || name == null) {
            return false;
        }
        if (!RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        logger.warn("Ignoring the header parameter '{}' of {}: the SDK controls this header.", name, where);
        return true;
    }

    @Nullable
    private static Parameter resolve(OpenAPI openAPI, Parameter parameter) {
        final String ref = parameter.get$ref();
        if (ref == null) {
            return parameter;
        }
        if (openAPI.getComponents() == null || openAPI.getComponents().getParameters() == null) {
            return null;
        }
        return openAPI.getComponents().getParameters().get(ref.substring(ref.lastIndexOf('/') + 1));
    }
}
