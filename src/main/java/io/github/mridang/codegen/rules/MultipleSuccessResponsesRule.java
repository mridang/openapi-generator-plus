package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Gives an operation that declares two or more 2xx responses with different
 * schemas a single result type: a synthesised {@code oneOf} component named
 * {@code <OperationId>Response} whose variants are exactly those schemas.
 *
 * <p>Upstream picks the lowest 2xx response and decodes every success body with
 * it. This rule instead rewrites every 2xx response of such an operation to
 * point at the union (so {@code returnType} is the union in every language's
 * existing {@code oneOf} machinery) and records the original status-to-schema
 * mapping on the operation as {@link #X_SUCCESS_RESPONSES}, so templates can
 * decode the body by the actual status code.
 *
 * <p>Operations with a single 2xx schema, several 2xx codes sharing one schema,
 * or no-content 2xx responses next to one schema are left untouched.
 */
public class MultipleSuccessResponsesRule implements CustomNormalizationRule {

    /**
     * Operation extension holding the ordered list of
     * {@code {status, ref}} maps (one per 2xx status code) that were folded
     * into the union.
     */
    public static final String X_SUCCESS_RESPONSES = "x-success-responses";

    /** Operation extension holding the name of the synthesised union component. */
    public static final String X_SUCCESS_UNION = "x-success-union";

    private static final Pattern SUCCESS_CODE = Pattern.compile("2\\d\\d|2XX", Pattern.CASE_INSENSITIVE);
    private static final String REF_PREFIX = "#/components/schemas/";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        if (openAPI.getPaths() == null) {
            return;
        }
        for (final PathItem pathItem : openAPI.getPaths().values()) {
            for (final Operation operation : pathItem.readOperations()) {
                apply(openAPI, operation, logger);
            }
        }
    }

    private void apply(OpenAPI openAPI, Operation operation, Logger logger) {
        if (operation.getOperationId() == null || operation.getResponses() == null) {
            return;
        }
        // Numeric codes in ascending order; a 2XX range sorts after every explicit code.
        final TreeMap<Integer, String> codes = new TreeMap<>();
        for (final String code : operation.getResponses().keySet()) {
            if (SUCCESS_CODE.matcher(code).matches()) {
                codes.put(Character.isDigit(code.charAt(1)) && Character.isDigit(code.charAt(2))
                        ? Integer.parseInt(code) : Integer.MAX_VALUE, code);
            }
        }
        final Map<String, Schema<?>> byCode = new LinkedHashMap<>();
        for (final String code : codes.values()) {
            final Schema<?> schema = schemaOf(operation.getResponses().get(code));
            if (schema != null) {
                byCode.put(code, schema);
            }
        }
        final List<Schema<?>> distinct = new ArrayList<>();
        for (final Schema<?> schema : byCode.values()) {
            if (!distinct.contains(schema)) {
                distinct.add(schema);
            }
        }
        if (distinct.size() < 2) {
            return;
        }

        if (openAPI.getComponents() == null) {
            openAPI.setComponents(new Components());
        }
        final Components components = openAPI.getComponents();
        final String base = Character.toUpperCase(operation.getOperationId().charAt(0))
                + operation.getOperationId().substring(1);

        final List<String> refs = new ArrayList<>(java.util.Collections.nCopies(distinct.size(), (String) null));
        final List<Map<String, String>> mapping = new ArrayList<>();
        final List<Schema<?>> variantRefs = new ArrayList<>();
        for (final Map.Entry<String, Schema<?>> entry : byCode.entrySet()) {
            final Schema<?> schema = entry.getValue();
            final int index = distinct.indexOf(schema);
            String ref = refs.get(index);
            if (ref == null) {
                if (schema.get$ref() != null) {
                    ref = schema.get$ref();
                } else {
                    final String name = freeName(components, base + "Response" + (index + 1));
                    components.addSchemas(name, schema);
                    ref = REF_PREFIX + name;
                }
                refs.set(index, ref);
            }
            final Schema<?> variant = new Schema<>().$ref(ref);
            if (!variantRefs.contains(variant)) {
                variantRefs.add(variant);
            }
            final Map<String, String> row = new LinkedHashMap<>();
            row.put("status", entry.getKey().toUpperCase(Locale.ROOT));
            row.put("ref", ref);
            mapping.add(row);
        }
        final String unionName = freeName(components, base + "Response");
        final Schema<Object> union = new Schema<>();
        union.setOneOf(new ArrayList<>(variantRefs));
        union.setDescription("One of the success responses of " + operation.getOperationId() + ".");
        components.addSchemas(unionName, union);

        for (final String code : byCode.keySet()) {
            final ApiResponse response = operation.getResponses().get(code);
            final Content content = response == null ? null : response.getContent();
            if (content == null) {
                continue;
            }
            for (final MediaType mediaType : content.values()) {
                if (mediaType.getSchema() != null) {
                    mediaType.setSchema(new Schema<>().$ref(REF_PREFIX + unionName));
                }
            }
        }
        operation.addExtension(X_SUCCESS_RESPONSES, mapping);
        operation.addExtension(X_SUCCESS_UNION, unionName);
        logger.info("Operation '{}' has {} distinct 2xx schemas; synthesised union '{}'.",
                operation.getOperationId(), variantRefs.size(), unionName);
    }

    @Nullable
    private static Schema<?> schemaOf(@Nullable ApiResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return null;
        }
        final MediaType json = response.getContent().get("application/json");
        final MediaType chosen = json != null ? json : response.getContent().values().iterator().next();
        return chosen.getSchema();
    }

    private static String freeName(Components components, String wanted) {
        String name = wanted;
        int n = 2;
        while (components.getSchemas() != null && components.getSchemas().containsKey(name)) {
            name = wanted + n++;
        }
        return name;
    }
}
