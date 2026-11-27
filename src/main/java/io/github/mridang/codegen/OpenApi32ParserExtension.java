package io.github.mridang.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ClasspathHelper;
import io.swagger.v3.parser.util.RemoteUrl;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Loads OpenAPI 3.2 documents, which the pinned swagger-parser rejects.
 *
 * <p>swagger-parser 2.1 returns no document for an {@code openapi} version that does not start
 * with {@code 3.0} or {@code 3.1}, and swagger-models 2.2 has no field for the 3.2 additions. The
 * parser asks every {@link SwaggerParserExtension} on the class path once its own reader gave up,
 * so this extension is reached only for a document the stock reader refused. It reads the
 * document as written, maps the 3.2 constructs the generators realise onto their 3.1 form, and
 * hands the result to the stock 3.1 reader:
 *
 * <ul>
 *   <li>{@code $self} becomes the base URI that relative references are resolved against, and a
 *       reference that points back into the document itself becomes a local one;
 *   <li>a Media Type Object referenced from {@code components.mediaTypes} is inlined where it is
 *       referenced, so it generates exactly what the inline object generates;
 *   <li>a Media Type Object's {@code itemSchema} becomes its {@code schema} when it has none, the
 *       item schema of a streamed response (D120);
 *   <li>an Example Object's {@code dataValue} becomes its {@code value}, and a
 *       {@code serializedValue} under a JSON media type becomes the parsed value when there is no
 *       data value;
 *   <li>the {@code name} of a Server Object and the {@code summary}, {@code parent} and
 *       {@code kind} of a Tag Object, which the model has no field for, move to the carriers
 *       {@link #SERVER_NAME}, {@link #TAG_SUMMARY}, {@link #TAG_PARENT} and {@link #TAG_KIND}
 *       that the shared generator reads; no template reads a carrier;
 *   <li>the {@code summary} of a Response Object is dropped from the parsed copy; the generator
 *       reads it from the document as written ({@code RawSpecReader}).
 * </ul>
 *
 * <p>The parsed document keeps {@code openapi: 3.2.x} as its version. Everything else in the
 * document is read by the 3.1 reader, which reports each field it does not know.
 */
@SuppressFBWarnings(
        value = {"PATH_TRAVERSAL_IN", "IMPROPER_UNICODE"},
        justification = "The location is the input specification the generator was asked to read;"
                + " scheme and media type names are ASCII")
public final class OpenApi32ParserExtension implements SwaggerParserExtension {

    /** The carrier of a Server Object's {@code name}. */
    public static final String SERVER_NAME = "x-oas32-name";

    /** The carrier of a Tag Object's {@code summary}. */
    public static final String TAG_SUMMARY = "x-oas32-summary";

    /** The carrier of a Tag Object's {@code parent}. */
    public static final String TAG_PARENT = "x-oas32-parent";

    /** The carrier of a Tag Object's {@code kind}. */
    public static final String TAG_KIND = "x-oas32-kind";

    /** The version the converted document is read as. */
    private static final String READ_AS_VERSION = "3.1.1";

    /** The reference prefix of a reusable Media Type Object. */
    private static final String MEDIA_TYPES_PREFIX = "#/components/mediaTypes/";

    /** The path-item fields that hold an operation object, the 3.2 {@code query} included. */
    private static final Set<String> OPERATION_FIELDS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace", "query");

    @Override
    @Nullable public SwaggerParseResult readLocation(
            final String url, final List<AuthorizationValue> auth, final ParseOptions options) {
        final String content = readContent(url, auth);
        if (content == null) {
            return null;
        }
        return read(content, url, auth, options);
    }

    @Override
    @Nullable public SwaggerParseResult readContents(
            final String content, final List<AuthorizationValue> auth, final ParseOptions options) {
        return read(content, null, auth, options);
    }

    /**
     * Whether the tree declares an OpenAPI 3.2 document.
     *
     * @param root the document as written
     * @return true when {@code openapi} is a 3.2 version
     */
    public static boolean isOpenApi32(@Nullable final JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        final JsonNode version = root.get("openapi");
        return version != null && version.isTextual() && version.asText().trim().startsWith("3.2.");
    }

    @Nullable private static SwaggerParseResult read(
            final String content,
            @Nullable final String location,
            final List<AuthorizationValue> auth,
            final ParseOptions options) {
        final JsonNode tree;
        try {
            tree = Yaml.mapper().readTree(content);
        } catch (IOException e) {
            return null;
        }
        if (!isOpenApi32(tree)) {
            return null;
        }
        final ObjectNode root = (ObjectNode) tree;
        final String version = root.get("openapi").asText().trim();
        final String base = baseLocation(root, location);
        toParsable(root, base);
        final String converted;
        try {
            converted = Json.mapper().writeValueAsString(root);
        } catch (IOException e) {
            return SwaggerParseResult.ofError("Cannot read the OpenAPI 3.2 document: " + e.getMessage());
        }
        final SwaggerParseResult result =
                new OpenAPIV3Parser().readContents(converted, auth, options, base);
        if (result != null && result.getOpenAPI() != null) {
            result.getOpenAPI().setOpenapi(version);
        }
        return result;
    }

    /**
     * Returns where relative references of the document resolve from: its {@code $self} resolved
     * against the location it was read from, or that location when it declares none.
     *
     * @param root the document as written
     * @param location the path or URL the document was read from, or null
     * @return the base location, or null when neither is known
     */
    @Nullable static String baseLocation(final JsonNode root, @Nullable final String location) {
        final JsonNode self = root.get("$self");
        if (self == null || !self.isTextual() || self.asText().isBlank()) {
            return location;
        }
        final URI selfUri = URI.create(self.asText().trim());
        if (selfUri.isAbsolute()) {
            return "file".equals(selfUri.getScheme())
                    ? Paths.get(selfUri).toString()
                    : selfUri.toString();
        }
        if (location == null) {
            return null;
        }
        final URI resolved = locationUri(location).resolve(selfUri).normalize();
        return "file".equals(resolved.getScheme()) ? Paths.get(resolved).toString() : resolved.toString();
    }

    /**
     * Rewrites a 3.2 document tree in place into the form the 3.1 reader accepts. See the class
     * comment for the rules.
     *
     * @param root the document as written
     * @param base the base location of the document, or null
     */
    static void toParsable(final ObjectNode root, @Nullable final String base) {
        root.put("openapi", READ_AS_VERSION);
        root.remove("$self");
        if (base != null) {
            localiseSelfReferences(root, locationUri(base));
        }
        final JsonNode components = root.get("components");
        final JsonNode mediaTypes =
                components == null ? null : components.get("mediaTypes");
        inlineMediaTypes(root, mediaTypes);
        if (components instanceof ObjectNode) {
            ((ObjectNode) components).remove("mediaTypes");
        }
        rewriteMediaTypes(root, false);
        carryServerNames(root);
        final JsonNode tags = root.get("tags");
        if (tags instanceof ArrayNode) {
            for (final JsonNode tag : tags) {
                if (tag instanceof ObjectNode) {
                    carry((ObjectNode) tag, "summary", TAG_SUMMARY);
                    carry((ObjectNode) tag, "parent", TAG_PARENT);
                    carry((ObjectNode) tag, "kind", TAG_KIND);
                }
            }
        }
        dropResponseSummaries(root);
    }

    /** Replaces every reference that resolves to this document with its local fragment. */
    private static void localiseSelfReferences(final JsonNode node, final URI self) {
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final JsonNode ref = object.get("$ref");
            if (ref != null && ref.isTextual() && !ref.asText().startsWith("#")) {
                final String value = ref.asText();
                final int hash = value.indexOf('#');
                final String document = hash < 0 ? value : value.substring(0, hash);
                final String fragment = hash < 0 ? "" : value.substring(hash + 1);
                try {
                    if (sameDocument(self.resolve(URI.create(document)), self)) {
                        object.put("$ref", "#" + fragment);
                    }
                } catch (IllegalArgumentException e) {
                    // Not a URI reference: left for the reader to report.
                }
            }
        }
        for (final JsonNode child : node) {
            localiseSelfReferences(child, self);
        }
    }

    private static boolean sameDocument(final URI a, final URI b) {
        return a.normalize().toString().equals(b.normalize().toString());
    }

    /** Replaces every reference to a reusable Media Type Object with a copy of it. */
    private static void inlineMediaTypes(final JsonNode node, @Nullable final JsonNode mediaTypes) {
        if (node instanceof ObjectNode) {
            final Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            final List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            fields.forEachRemaining(entries::add);
            for (final Map.Entry<String, JsonNode> entry : entries) {
                final JsonNode target = mediaTypeTarget(entry.getValue(), mediaTypes, 0);
                if (target != null) {
                    ((ObjectNode) node).set(entry.getKey(), target.deepCopy());
                }
            }
        } else if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                final JsonNode target = mediaTypeTarget(array.get(i), mediaTypes, 0);
                if (target != null) {
                    array.set(i, target.deepCopy());
                }
            }
        }
        for (final JsonNode child : node) {
            inlineMediaTypes(child, mediaTypes);
        }
    }

    /** Follows a chain of references to {@code components.mediaTypes} to the object at its end. */
    @Nullable private static JsonNode mediaTypeTarget(
            final JsonNode node, @Nullable final JsonNode mediaTypes, final int hops) {
        if (!node.isObject() || mediaTypes == null || hops > 32) {
            return null;
        }
        final JsonNode ref = node.get("$ref");
        if (ref == null || !ref.isTextual() || !ref.asText().startsWith(MEDIA_TYPES_PREFIX)) {
            return null;
        }
        final String name = ref.asText().substring(MEDIA_TYPES_PREFIX.length())
                .replace("~1", "/").replace("~0", "~");
        final JsonNode target = mediaTypes.get(name);
        if (target == null) {
            return null;
        }
        final JsonNode further = mediaTypeTarget(target, mediaTypes, hops + 1);
        return further != null ? further : target;
    }

    /**
     * Visits every Media Type Object (the values of a {@code content} map) and every Example
     * Object under it, and rewrites their 3.2 fields.
     */
    private static void rewriteMediaTypes(final JsonNode node, final boolean nameKeyed) {
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            if (!nameKeyed) {
                final JsonNode content = object.get("content");
                if (content instanceof ObjectNode) {
                    final Iterator<Map.Entry<String, JsonNode>> media = content.properties().iterator();
                    while (media.hasNext()) {
                        final Map.Entry<String, JsonNode> entry = media.next();
                        if (entry.getValue() instanceof ObjectNode) {
                            rewriteMediaType(entry.getKey(), (ObjectNode) entry.getValue());
                        }
                    }
                }
                final JsonNode examples = object.get("examples");
                if (examples instanceof ObjectNode && !object.has("content")) {
                    rewriteExamples((ObjectNode) examples, false);
                }
            }
            final Iterator<Map.Entry<String, JsonNode>> fields = object.properties().iterator();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                rewriteMediaTypes(field.getValue(), !nameKeyed && isNameKeyed(field.getKey()));
            }
        } else if (node instanceof ArrayNode) {
            for (final JsonNode child : node) {
                rewriteMediaTypes(child, false);
            }
        }
    }

    private static boolean isNameKeyed(final String key) {
        return "properties".equals(key) || "paths".equals(key) || "schemas".equals(key)
                || "patternProperties".equals(key) || "$defs".equals(key);
    }

    private static void rewriteMediaType(final String mediaType, final ObjectNode object) {
        if (object.has("itemSchema")) {
            final JsonNode itemSchema = object.remove("itemSchema");
            if (!object.has("schema")) {
                object.set("schema", itemSchema);
            }
        }
        final JsonNode examples = object.get("examples");
        if (examples instanceof ObjectNode) {
            rewriteExamples((ObjectNode) examples, isJson(mediaType));
        }
    }

    private static boolean isJson(final String mediaType) {
        final String base = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return base.equals("application/json") || base.endsWith("+json");
    }

    private static void rewriteExamples(final ObjectNode examples, final boolean json) {
        final Iterator<Map.Entry<String, JsonNode>> entries = examples.properties().iterator();
        while (entries.hasNext()) {
            final JsonNode example = entries.next().getValue();
            if (example instanceof ObjectNode) {
                rewriteExample((ObjectNode) example, json);
            }
        }
    }

    /**
     * Rewrites one Example Object: {@code dataValue} becomes {@code value}; without one, a
     * {@code serializedValue} that parses as JSON under a JSON media type becomes the value.
     *
     * @param example the Example Object, changed in place
     * @param json whether the example belongs to a JSON media type
     */
    static void rewriteExample(final ObjectNode example, final boolean json) {
        final JsonNode dataValue = example.remove("dataValue");
        final JsonNode serializedValue = example.remove("serializedValue");
        if (example.has("value") || example.has("$ref")) {
            return;
        }
        if (dataValue != null) {
            example.set("value", dataValue);
        } else if (json && serializedValue != null && serializedValue.isTextual()) {
            try {
                example.set("value", Json.mapper().readTree(serializedValue.asText()));
            } catch (IOException e) {
                example.set("value", TextNode.valueOf(serializedValue.asText()));
            }
        }
    }

    /** Moves a field to its carrier, replacing a carrier the document set itself. */
    private static void carry(final ObjectNode object, final String field, final String carrier) {
        final JsonNode value = object.remove(field);
        if (value != null) {
            object.set(carrier, value);
        }
    }

    /** Carries the {@code name} of every Server Object: the root, path-item and operation lists. */
    private static void carryServerNames(final ObjectNode root) {
        carryNames(root.get("servers"));
        final JsonNode paths = root.get("paths");
        if (paths == null) {
            return;
        }
        for (final JsonNode pathItem : paths) {
            carryNames(pathItem.get("servers"));
            for (final String method : OPERATION_FIELDS) {
                final JsonNode operation = pathItem.get(method);
                if (operation != null) {
                    carryNames(operation.get("servers"));
                }
            }
        }
    }

    private static void carryNames(@Nullable final JsonNode servers) {
        if (servers instanceof ArrayNode) {
            for (final JsonNode server : servers) {
                if (server instanceof ObjectNode) {
                    carry((ObjectNode) server, "name", SERVER_NAME);
                }
            }
        }
    }

    /** Drops {@code summary} from every Response Object of every operation and the components. */
    private static void dropResponseSummaries(final ObjectNode root) {
        final JsonNode paths = root.get("paths");
        if (paths != null) {
            for (final JsonNode pathItem : paths) {
                for (final String method : OPERATION_FIELDS) {
                    final JsonNode operation = pathItem.get(method);
                    if (operation != null) {
                        dropSummaries(operation.get("responses"));
                    }
                }
            }
        }
        final JsonNode components = root.get("components");
        if (components != null) {
            dropSummaries(components.get("responses"));
        }
    }

    private static void dropSummaries(@Nullable final JsonNode responses) {
        if (responses instanceof ObjectNode) {
            for (final JsonNode response : responses) {
                if (response instanceof ObjectNode && !response.has("$ref")) {
                    ((ObjectNode) response).remove("summary");
                }
            }
        }
    }

    private static URI locationUri(final String location) {
        final String lower = location.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("file:")) {
            return URI.create(location);
        }
        return Paths.get(location).toAbsolutePath().normalize().toUri();
    }

    @SuppressFBWarnings(
            value = "REC_CATCH_EXCEPTION",
            justification = "RemoteUrl.urlToString declares Exception; any failure means not read")
    @Nullable private static String readContent(
            final String location, final List<AuthorizationValue> auth) {
        try {
            final String lower = location.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http:") || lower.startsWith("https:")) {
                return RemoteUrl.urlToString(location, auth == null ? List.of() : auth);
            }
            final Path path = lower.startsWith("file:")
                    ? Paths.get(URI.create(location))
                    : Paths.get(location);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            return ClasspathHelper.loadFileFromClasspath(location);
        } catch (Exception e) {
            return null;
        }
    }
}
