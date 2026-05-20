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
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements a custom rule that maps OAS 3.1 {@code contentEncoding} and
 * {@code contentMediaType} keywords onto the existing OAS 3.0
 * {@code format: byte} / {@code format: binary} machinery so the rest of the
 * generator (which still understands the 3.0 idiom) continues to work
 * without changes to every per-language model template.
 *
 * <p>Translation:
 * <ul>
 *   <li>{@code contentEncoding: base64}    -&gt; {@code format: byte}</li>
 *   <li>{@code contentEncoding: base64url} -&gt; {@code format: byte} +
 *       {@code x-is-base64url: true} flag (per-lang templates can use this
 *       to switch to URL-safe decoders)</li>
 *   <li>{@code contentEncoding: base16}    -&gt; {@code format: byte} +
 *       {@code x-is-base16: true} flag</li>
 *   <li>{@code contentEncoding: binary} (or {@code 7bit}/{@code 8bit})
 *       -&gt; {@code format: binary}</li>
 *   <li>any other value -&gt; leave as plain string, emit a warning</li>
 * </ul>
 *
 * <p>{@code contentMediaType} is appended to the schema's description as
 * {@code "Content media type: <value>"} so generated docstrings record the
 * intended payload type.
 *
 * <p>The rule walks every schema reachable from the spec: components,
 * parameters, request bodies, and responses, recursing through
 * {@code properties}, {@code items}, {@code allOf}/{@code anyOf}/{@code oneOf},
 * and {@code additionalProperties}.
 */
public class ContentEncodingRule implements CustomNormalizationRule {

    static final String X_IS_BASE64_URL = "x-is-base64url";
    static final String X_IS_BASE16 = "x-is-base16";
    private static final String DOCSTRING_PREFIX = "Content media type: ";

    @Override
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        logger.info("Starting CONTENT_ENCODING rule (OAS 3.1 contentEncoding/contentMediaType).");

        Set<Schema<?>> visited = new HashSet<>();

        // Walk components.schemas
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            for (Schema<?> schema : openAPI.getComponents().getSchemas().values()) {
                walk(schema, visited, logger);
            }
        }

        // Walk all paths -> operations -> parameters / requestBody / responses
        if (openAPI.getPaths() != null) {
            for (PathItem pathItem : openAPI.getPaths().values()) {
                for (Operation operation : pathItem.readOperations()) {
                    walkParameters(operation.getParameters(), visited, logger);
                    walkRequestBody(operation.getRequestBody(), visited, logger);
                    walkResponses(operation, visited, logger);
                }
                walkParameters(pathItem.getParameters(), visited, logger);
            }
        }

        logger.info("CONTENT_ENCODING rule completed.");
    }

    private void walkParameters(@Nullable List<Parameter> parameters,
                                Set<Schema<?>> visited, Logger logger) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            walk(parameter.getSchema(), visited, logger);
            walkContent(parameter.getContent(), visited, logger);
        }
    }

    private void walkRequestBody(@Nullable RequestBody requestBody,
                                 Set<Schema<?>> visited, Logger logger) {
        if (requestBody == null) {
            return;
        }
        walkContent(requestBody.getContent(), visited, logger);
    }

    private void walkResponses(Operation operation, Set<Schema<?>> visited, Logger logger) {
        if (operation.getResponses() == null) {
            return;
        }
        for (ApiResponse response : operation.getResponses().values()) {
            walkContent(response.getContent(), visited, logger);
        }
    }

    private void walkContent(@Nullable Content content, Set<Schema<?>> visited, Logger logger) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            walk(mediaType.getSchema(), visited, logger);
        }
    }

    /**
     * Recursively visits a schema, applying the contentEncoding/contentMediaType
     * transformation and then descending into composite parts.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void walk(@Nullable Schema schema, Set<Schema<?>> visited, Logger logger) {
        if (schema == null || schema.get$ref() != null) {
            return;
        }
        if (!visited.add(schema)) {
            return;
        }

        applyToSchema(schema, logger);

        // properties
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            for (Schema<?> child : properties.values()) {
                walk(child, visited, logger);
            }
        }
        // items
        if (schema.getItems() != null) {
            walk(schema.getItems(), visited, logger);
        }
        // additionalProperties
        Object addProps = schema.getAdditionalProperties();
        if (addProps instanceof Schema) {
            walk((Schema<?>) addProps, visited, logger);
        }
        // allOf / anyOf / oneOf
        walkList(schema.getAllOf(), visited, logger);
        walkList(schema.getAnyOf(), visited, logger);
        walkList(schema.getOneOf(), visited, logger);
        // not
        if (schema.getNot() != null) {
            walk(schema.getNot(), visited, logger);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void walkList(@Nullable List<Schema> list, Set<Schema<?>> visited, Logger logger) {
        if (list == null) {
            return;
        }
        for (Schema<?> s : new ArrayList<Schema>(list)) {
            walk(s, visited, logger);
        }
    }

    /**
     * Applies the {@code contentEncoding} / {@code contentMediaType} mapping
     * to a single, non-ref schema. Idempotent: clears the 3.1 keywords once
     * mapped so re-running the rule has no effect.
     */
    private void applyToSchema(Schema<?> schema, Logger logger) {
        String encoding = schema.getContentEncoding();
        String mediaType = schema.getContentMediaType();

        if (encoding != null && !encoding.isEmpty()) {
            String normalized = encoding.toLowerCase(java.util.Locale.ROOT);
            switch (normalized) {
                case "base64":
                    setFormatIfAbsent(schema, "byte", logger);
                    schema.setContentEncoding(null);
                    break;
                case "base64url":
                    setFormatIfAbsent(schema, "byte", logger);
                    schema.addExtension(X_IS_BASE64_URL, true);
                    schema.setContentEncoding(null);
                    break;
                case "base16":
                    setFormatIfAbsent(schema, "byte", logger);
                    schema.addExtension(X_IS_BASE16, true);
                    schema.setContentEncoding(null);
                    break;
                case "binary":
                case "7bit":
                case "8bit":
                    setFormatIfAbsent(schema, "binary", logger);
                    schema.setContentEncoding(null);
                    break;
                case "quoted-printable":
                    // Treated as plain text with a docstring note; warn loudly.
                    logger.warn("contentEncoding '{}' is not natively supported; leaving as plain string.", encoding);
                    appendDocstring(schema, "Content encoding: " + encoding);
                    schema.setContentEncoding(null);
                    break;
                default:
                    logger.warn("Unknown contentEncoding '{}'; leaving schema as plain string.", encoding);
                    appendDocstring(schema, "Content encoding: " + encoding);
                    schema.setContentEncoding(null);
                    break;
            }
        }

        if (mediaType != null && !mediaType.isEmpty()) {
            appendDocstring(schema, DOCSTRING_PREFIX + mediaType);
            schema.setContentMediaType(null);
        }
    }

    private void setFormatIfAbsent(Schema<?> schema, String format, Logger logger) {
        if (schema.getFormat() == null || schema.getFormat().isEmpty()) {
            schema.setFormat(format);
        } else if (!schema.getFormat().equals(format)) {
            logger.warn("Schema already has format '{}'; not overwriting with '{}'.",
                schema.getFormat(), format);
        }
    }

    private void appendDocstring(Schema<?> schema, String addition) {
        String existing = schema.getDescription();
        if (existing == null || existing.isEmpty()) {
            schema.setDescription(addition);
        } else if (!existing.contains(addition)) {
            schema.setDescription(existing + "\n" + addition);
        }
    }
}
