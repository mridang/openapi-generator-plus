package io.github.mridang.codegen.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Applies the {@code summary} and {@code description} that a Reference Object carries next to
 * its {@code $ref} (OpenAPI 3.1 and 3.2, "Reference Object"): they override the text of the
 * object the reference points to, for that use only.
 *
 * <p>The stock generator follows a reference and keeps only the target, so the text written
 * beside the reference is lost. For every parameter, request body, response, response header
 * and example of every operation (and every path-item parameter) that is a local reference with
 * a sibling {@code description} or {@code summary}, this rule replaces the reference with a copy
 * of its target that carries the overriding text. The component itself is left unchanged, so
 * every other use keeps its own text. A reference without sibling text is left alone, and so
 * is one that does not resolve inside the document. A path item reference is handled by
 * {@link InlinePathItemReferencesRule}, where the referencing item's fields already win.
 */
public class ApplyReferenceOverridesRule implements CustomNormalizationRule {

    private static final int MAX_HOPS = 32;

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getPaths() == null) {
            return;
        }
        final Components components = openAPI.getComponents();
        for (PathItem item : openAPI.getPaths().values()) {
            if (item == null) {
                continue;
            }
            overrideParameters(item.getParameters(), components, logger);
            for (Operation operation : item.readOperations()) {
                overrideParameters(operation.getParameters(), components, logger);
                final RequestBody body = operation.getRequestBody();
                if (body != null && hasOverride(body.get$ref(), body.getDescription(), null)) {
                    final RequestBody target = resolve(body.get$ref(), "requestBodies",
                            components == null ? null : components.getRequestBodies(),
                            RequestBody::get$ref);
                    final RequestBody copy = copy(target, RequestBody.class, logger);
                    if (copy != null) {
                        copy.setDescription(body.getDescription());
                        operation.setRequestBody(copy);
                    }
                }
                if (body != null) {
                    overrideExamples(body.getContent(), components, logger);
                }
                if (operation.getResponses() != null) {
                    for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
                        overrideResponse(entry, components, logger);
                    }
                }
            }
        }
    }

    private static void overrideResponse(Map.Entry<String, ApiResponse> entry,
                                         @Nullable Components components, Logger logger) {
        ApiResponse response = entry.getValue();
        if (response == null) {
            return;
        }
        if (hasOverride(response.get$ref(), response.getDescription(), null)) {
            final ApiResponse target = resolve(response.get$ref(), "responses",
                    components == null ? null : components.getResponses(), ApiResponse::get$ref);
            final ApiResponse copy = copy(target, ApiResponse.class, logger);
            if (copy != null) {
                copy.setDescription(response.getDescription());
                entry.setValue(copy);
                response = copy;
            }
        }
        if (response.getHeaders() != null) {
            for (Map.Entry<String, Header> header : response.getHeaders().entrySet()) {
                final Header value = header.getValue();
                if (value != null && hasOverride(value.get$ref(), value.getDescription(), null)) {
                    final Header target = resolve(value.get$ref(), "headers",
                            components == null ? null : components.getHeaders(), Header::get$ref);
                    final Header copy = copy(target, Header.class, logger);
                    if (copy != null) {
                        copy.setDescription(value.getDescription());
                        header.setValue(copy);
                    }
                }
            }
        }
        overrideExamples(response.getContent(), components, logger);
    }

    private static void overrideParameters(@Nullable List<Parameter> parameters,
                                           @Nullable Components components, Logger logger) {
        if (parameters == null) {
            return;
        }
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = parameters.get(i);
            if (parameter == null
                    || !hasOverride(parameter.get$ref(), parameter.getDescription(), null)) {
                continue;
            }
            final Parameter target = resolve(parameter.get$ref(), "parameters",
                    components == null ? null : components.getParameters(), Parameter::get$ref);
            final Parameter copy = copy(target, Parameter.class, logger);
            if (copy != null) {
                copy.setDescription(parameter.getDescription());
                parameters.set(i, copy);
            }
        }
    }

    private static void overrideExamples(@Nullable Content content,
                                         @Nullable Components components, Logger logger) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType == null || mediaType.getExamples() == null) {
                continue;
            }
            for (Map.Entry<String, Example> entry : mediaType.getExamples().entrySet()) {
                final Example example = entry.getValue();
                if (example == null || !hasOverride(example.get$ref(), example.getDescription(),
                        example.getSummary())) {
                    continue;
                }
                final Example target = resolve(example.get$ref(), "examples",
                        components == null ? null : components.getExamples(), Example::get$ref);
                final Example copy = copy(target, Example.class, logger);
                if (copy != null) {
                    if (example.getSummary() != null) {
                        copy.setSummary(example.getSummary());
                    }
                    if (example.getDescription() != null) {
                        copy.setDescription(example.getDescription());
                    }
                    entry.setValue(copy);
                }
            }
        }
    }

    private static boolean hasOverride(@Nullable String ref, @Nullable String description,
                                       @Nullable String summary) {
        return ref != null && (description != null || summary != null);
    }

    /** Follows a chain of local references into one component map to the object at its end. */
    @Nullable
    private static <T> T resolve(@Nullable String ref, String kind, @Nullable Map<String, T> map,
                                 Function<T, String> refOf) {
        final String prefix = "#/components/" + kind + "/";
        String current = ref;
        for (int hops = 0; hops < MAX_HOPS && current != null; hops++) {
            if (!current.startsWith(prefix) || map == null) {
                return null;
            }
            final String name = current.substring(prefix.length())
                    .replace("~1", "/").replace("~0", "~");
            final T target = map.get(name);
            if (target == null) {
                return null;
            }
            current = refOf.apply(target);
            if (current == null) {
                return target;
            }
        }
        return null;
    }

    @Nullable
    private static <T> T copy(@Nullable T source, Class<T> type, Logger logger) {
        if (source == null) {
            return null;
        }
        try {
            final ObjectMapper mapper = Json31.mapper();
            return mapper.readValue(mapper.writeValueAsString(source), type);
        } catch (IOException e) {
            logger.warn("A referenced {} could not be copied: {}", type.getSimpleName(),
                    e.getClass().getName());
            return null;
        }
    }
}
