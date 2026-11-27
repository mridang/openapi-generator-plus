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

    /**
     * The carrier of the HTTP method of an operation the 3.1 model has no field for: one declared
     * under a path item's {@code query} field or in its {@code additionalOperations} map. It sits
     * on the Operation Object, which is moved into a path item of its own (see
     * {@link #resourcePath}).
     */
    public static final String OPERATION_METHOD = "x-oas32-method";

    /**
     * The carrier, on the root of the document, of the problems this loader found and left out of
     * the parsed document; {@link SpecWarnings} reports each one with its pointer. Each entry is a
     * map with a {@code pointer} and a {@code message}.
     */
    public static final String LOADER_WARNINGS = "x-oas32-warnings";

    /**
     * Separates the path from the method in the key of a path item that holds one operation the
     * 3.1 model cannot place. A space is never part of a path template (D159), so the key never
     * clashes with a real path.
     */
    private static final String METHOD_SEPARATOR = " ";

    /**
     * The custom method names the SDKs can send: upper-case letters, digits and underscores,
     * starting with a letter. Ruby and Elixir write the method as a bare symbol or atom, and
     * several HTTP libraries upper-case the method they are given, so a method outside this set
     * could not be sent exactly as written in every language (D160).
     */
    private static final java.util.regex.Pattern CUSTOM_METHOD =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * The carrier of a parameter's 3.2 location {@code querystring}, which the loader rewrites to
     * an exploded form-style query parameter (D161).
     */
    public static final String PARAMETER_IN = "x-oas32-in";

    /**
     * The carrier of a cookie parameter's 3.2 {@code style: cookie}, which the parser's style enum
     * cannot hold (D163).
     */
    public static final String PARAMETER_STYLE = "x-oas32-style";

    /** The 3.2 cookie style. */
    public static final String COOKIE_STYLE = "cookie";

    /** The only media type a querystring parameter is read with. */
    private static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";

    /** The version the converted document is read as. */
    private static final String READ_AS_VERSION = "3.1.1";

    /** The reference prefix of a reusable Media Type Object. */
    private static final String MEDIA_TYPES_PREFIX = "#/components/mediaTypes/";

    /** The path-item fields that hold an operation object, the 3.2 {@code query} included. */
    private static final List<String> OPERATION_FIELD_ORDER =
            List.of("get", "put", "post", "delete", "options", "head", "patch", "trace", "query");

    /** The same fields, for membership checks. */
    private static final Set<String> OPERATION_FIELDS = Set.copyOf(OPERATION_FIELD_ORDER);

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
        final ArrayNode warnings = root.arrayNode();
        relocateOperations(root, warnings);
        rewriteQueryStrings(root, warnings);
        if (!warnings.isEmpty()) {
            root.set(LOADER_WARNINGS, warnings);
        }
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

    /**
     * Returns the path a path-item key stands for: the key itself, or, for the key of a path item
     * that holds an operation of a method the 3.1 model has no field for, the key without the
     * method.
     *
     * @param key a key of the {@code paths} map
     * @return the path template the key names
     */
    public static String resourcePath(final String key) {
        final int separator = key.indexOf(METHOD_SEPARATOR);
        return separator < 0 ? key : key.substring(0, separator);
    }

    /**
     * Returns the HTTP method an operation was declared with when the 3.1 model has no field for
     * it ({@code QUERY}, or a method of {@code additionalOperations}), or null for an operation
     * of one of the eight fields the model knows.
     *
     * @param operation the parsed operation, or null
     * @return the method exactly as declared, or null
     */
    @Nullable public static String declaredMethod(@Nullable final io.swagger.v3.oas.models.Operation operation) {
        if (operation == null || operation.getExtensions() == null) {
            return null;
        }
        final Object method = operation.getExtensions().get(OPERATION_METHOD);
        return method instanceof String ? (String) method : null;
    }

    /**
     * Moves every operation the 3.1 model has no field for into a path item of its own (D159,
     * D160). The 3.1 Path Item holds one operation per fixed method, and the generator reads
     * operations from those fields only, so an operation under {@code query} or in
     * {@code additionalOperations} goes into the {@code post} field of a new path item keyed
     * {@code "<path> <METHOD>"}, right after its own path item, carrying its method in
     * {@link #OPERATION_METHOD} and a copy of the path item's {@code summary},
     * {@code description}, {@code servers} and {@code parameters}. A path item that is a local
     * reference to {@code components.pathItems} contributes the operations of its target. An
     * {@code additionalOperations} entry whose method is a fixed field of the path item, or is
     * not a method the SDKs can send (see {@link #CUSTOM_METHOD}), is left out and reported.
     *
     * @param root the document as written, changed in place
     * @param warnings the list the problems are added to
     */
    static void relocateOperations(final ObjectNode root, final ArrayNode warnings) {
        final JsonNode paths = root.get("paths");
        final JsonNode components = root.get("components");
        final JsonNode pathItems = components == null ? null : components.get("pathItems");
        if (paths instanceof ObjectNode) {
            final ObjectNode rebuilt = root.objectNode();
            final Iterator<Map.Entry<String, JsonNode>> entries = paths.properties().iterator();
            boolean changed = false;
            while (entries.hasNext()) {
                final Map.Entry<String, JsonNode> entry = entries.next();
                rebuilt.set(entry.getKey(), entry.getValue());
                if (!(entry.getValue() instanceof ObjectNode)) {
                    continue;
                }
                final ObjectNode item = (ObjectNode) entry.getValue();
                final ObjectNode source = referencedPathItem(item, pathItems);
                final Map<String, ObjectNode> extras = new java.util.LinkedHashMap<>();
                if (source != null) {
                    extras.putAll(extraOperations(source, refPointer(item), warnings));
                }
                extras.putAll(extraOperations(item, "#/paths/" + escape(entry.getKey()), warnings));
                for (final Map.Entry<String, ObjectNode> extra : extras.entrySet()) {
                    final ObjectNode synthetic = root.objectNode();
                    copyShared(source, synthetic);
                    copyShared(item, synthetic);
                    extra.getValue().put(OPERATION_METHOD, extra.getKey());
                    synthetic.set("post", extra.getValue());
                    rebuilt.set(entry.getKey() + METHOD_SEPARATOR + extra.getKey(), synthetic);
                    changed = true;
                }
                item.remove("query");
                item.remove("additionalOperations");
            }
            if (changed) {
                root.set("paths", rebuilt);
            }
        }
        if (pathItems instanceof ObjectNode) {
            final Iterator<Map.Entry<String, JsonNode>> items = pathItems.properties().iterator();
            while (items.hasNext()) {
                final JsonNode item = items.next().getValue();
                if (item instanceof ObjectNode) {
                    ((ObjectNode) item).remove("query");
                    ((ObjectNode) item).remove("additionalOperations");
                }
            }
        }
    }

    /** The target of a path item that is a local reference to {@code components.pathItems}. */
    @Nullable private static ObjectNode referencedPathItem(
            final ObjectNode item, @Nullable final JsonNode pathItems) {
        final JsonNode ref = item.get("$ref");
        final String prefix = "#/components/pathItems/";
        if (pathItems == null || ref == null || !ref.isTextual() || !ref.asText().startsWith(prefix)) {
            return null;
        }
        final JsonNode target = pathItems.get(
                ref.asText().substring(prefix.length()).replace("~1", "/").replace("~0", "~"));
        return target instanceof ObjectNode ? (ObjectNode) target : null;
    }

    private static String refPointer(final ObjectNode item) {
        return item.get("$ref").asText();
    }

    /**
     * The operations of a path item that the 3.1 model has no field for, keyed by their method in
     * declaration order: {@code query} first, then each entry of {@code additionalOperations}.
     */
    private static Map<String, ObjectNode> extraOperations(
            final ObjectNode item, final String pointer, final ArrayNode warnings) {
        final Map<String, ObjectNode> extras = new java.util.LinkedHashMap<>();
        final JsonNode query = item.get("query");
        if (query instanceof ObjectNode) {
            extras.put("QUERY", ((ObjectNode) query).deepCopy());
        }
        final JsonNode additional = item.get("additionalOperations");
        if (additional instanceof ObjectNode) {
            final Iterator<Map.Entry<String, JsonNode>> methods = additional.properties().iterator();
            while (methods.hasNext()) {
                final Map.Entry<String, JsonNode> method = methods.next();
                final String at = pointer + "/additionalOperations/" + escape(method.getKey());
                if (!(method.getValue() instanceof ObjectNode)) {
                    continue;
                }
                if (OPERATION_FIELDS.contains(method.getKey().toLowerCase(Locale.ROOT))) {
                    warn(warnings, at, "the method '" + method.getKey() + "' has its own field in"
                            + " the path item and must not be in additionalOperations; the"
                            + " operation is ignored");
                } else if (!CUSTOM_METHOD.matcher(method.getKey()).matches()) {
                    warn(warnings, at, "the method '" + method.getKey() + "' is not supported:"
                            + " a custom method must be upper-case letters, digits and"
                            + " underscores, starting with a letter; the operation is ignored");
                } else {
                    extras.put(method.getKey(), ((ObjectNode) method.getValue()).deepCopy());
                }
            }
        }
        return extras;
    }

    /** Copies the fields a path item shares with all its operations. */
    private static void copyShared(@Nullable final ObjectNode from, final ObjectNode into) {
        if (from == null) {
            return;
        }
        for (final String field : List.of("summary", "description", "servers", "parameters")) {
            final JsonNode value = from.get(field);
            if (value != null) {
                into.set(field, value.deepCopy());
            }
        }
    }

    private static void warn(final ArrayNode warnings, final String pointer, final String message) {
        warnings.addObject().put("pointer", pointer).put("message", message);
    }

    private static String escape(final String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Rewrites every {@code in: querystring} parameter (D161). A querystring parameter whose
     * {@code content} is {@code application/x-www-form-urlencoded} is the query string written as
     * the form pairs of its value, which is exactly an exploded form-style query parameter of the
     * same schema, so it becomes one: {@code in: query}, {@code style: form},
     * {@code explode: true}, the content's schema as its {@code schema}. A querystring parameter
     * with any other media type is left out and reported, together with every reference to it.
     * An operation whose querystring parameter sits beside a query parameter, or beside a second
     * querystring parameter, is reported, since the specification forbids both; its parameters
     * are kept.
     *
     * @param root the document as written, changed in place
     * @param warnings the list the problems are added to
     */
    static void rewriteQueryStrings(final ObjectNode root, final ArrayNode warnings) {
        final JsonNode components = root.get("components");
        final JsonNode parameters = components == null ? null : components.get("parameters");
        final Set<String> unsupported = new java.util.HashSet<>();
        if (parameters instanceof ObjectNode) {
            final Iterator<Map.Entry<String, JsonNode>> named = parameters.properties().iterator();
            while (named.hasNext()) {
                final Map.Entry<String, JsonNode> entry = named.next();
                if (entry.getValue() instanceof ObjectNode && !rewriteQueryString(
                        (ObjectNode) entry.getValue(),
                        "#/components/parameters/" + escape(entry.getKey()), warnings)) {
                    unsupported.add("#/components/parameters/" + escape(entry.getKey()));
                }
            }
            for (final String ref : unsupported) {
                ((ObjectNode) parameters).remove(
                        ref.substring("#/components/parameters/".length())
                                .replace("~1", "/").replace("~0", "~"));
            }
        }
        final JsonNode paths = root.get("paths");
        if (paths != null) {
            final Iterator<Map.Entry<String, JsonNode>> items = paths.properties().iterator();
            while (items.hasNext()) {
                final Map.Entry<String, JsonNode> item = items.next();
                final String pointer = "#/paths/" + escape(item.getKey());
                final JsonNode shared = item.getValue().get("parameters");
                rewriteParameterList(shared, pointer + "/parameters", unsupported, warnings);
                for (final String field : OPERATION_FIELD_ORDER) {
                    final JsonNode operation = item.getValue().get(field);
                    if (operation instanceof ObjectNode) {
                        final JsonNode own = operation.get("parameters");
                        rewriteParameterList(own, pointer + "/" + field + "/parameters", unsupported,
                                warnings);
                        checkQueryStringExclusive(root, shared, own, pointer + "/" + field,
                                warnings);
                    }
                }
            }
        }
    }

    /** Rewrites the querystring parameters of one list and drops the ones that are left out. */
    private static void rewriteParameterList(@Nullable final JsonNode list, final String pointer,
            final Set<String> unsupported, final ArrayNode warnings) {
        if (!(list instanceof ArrayNode)) {
            return;
        }
        final ArrayNode array = (ArrayNode) list;
        for (int i = array.size() - 1; i >= 0; i--) {
            final JsonNode parameter = array.get(i);
            final JsonNode ref = parameter.get("$ref");
            if (ref != null && ref.isTextual() && unsupported.contains(ref.asText())) {
                array.remove(i);
            } else if (parameter instanceof ObjectNode
                    && !rewriteQueryString((ObjectNode) parameter, pointer + "/" + i, warnings)) {
                array.remove(i);
            }
        }
    }

    /**
     * Rewrites one querystring parameter in place; returns false when it is left out. Any other
     * parameter is kept, with a {@code style: cookie} moved to {@link #PARAMETER_STYLE} (D163).
     */
    private static boolean rewriteQueryString(
            final ObjectNode parameter, final String pointer, final ArrayNode warnings) {
        final JsonNode style = parameter.get("style");
        if (style != null && COOKIE_STYLE.equals(style.asText())) {
            parameter.remove("style");
            parameter.put(PARAMETER_STYLE, COOKIE_STYLE);
        }
        final JsonNode in = parameter.get("in");
        if (in == null || !"querystring".equals(in.asText())) {
            return true;
        }
        final JsonNode content = parameter.get("content");
        final JsonNode form = content == null ? null : content.get(FORM_MEDIA_TYPE);
        if (content == null || content.size() != 1 || !(form instanceof ObjectNode)) {
            warn(warnings, pointer, "a querystring parameter is supported only with"
                    + " application/x-www-form-urlencoded content; the parameter is ignored");
            return false;
        }
        parameter.put("in", "query");
        parameter.put("style", "form");
        parameter.put("explode", true);
        parameter.remove("content");
        parameter.remove("allowReserved");
        final JsonNode schema = form.get("schema");
        parameter.set("schema", schema == null ? parameter.objectNode().put("type", "object")
                : schema.deepCopy());
        parameter.put(PARAMETER_IN, "querystring");
        return true;
    }

    /** Reports an operation that mixes its querystring parameter with query parameters. */
    private static void checkQueryStringExclusive(final ObjectNode root,
            @Nullable final JsonNode shared, @Nullable final JsonNode own, final String pointer,
            final ArrayNode warnings) {
        int queryStrings = 0;
        int queries = 0;
        for (final JsonNode list : java.util.Arrays.asList(shared, own)) {
            if (!(list instanceof ArrayNode)) {
                continue;
            }
            for (final JsonNode parameter : list) {
                final JsonNode resolved = resolveParameter(root, parameter);
                if (resolved == null || resolved.get("in") == null) {
                    continue;
                }
                if (resolved.has(PARAMETER_IN)) {
                    queryStrings++;
                } else if ("query".equals(resolved.get("in").asText())) {
                    queries++;
                }
            }
        }
        if (queryStrings > 1) {
            warn(warnings, pointer, "an operation has at most one querystring parameter");
        }
        if (queryStrings > 0 && queries > 0) {
            warn(warnings, pointer, "a querystring parameter must not be used together with query"
                    + " parameters; both are sent");
        }
    }

    @Nullable private static JsonNode resolveParameter(final ObjectNode root, final JsonNode parameter) {
        final JsonNode ref = parameter.get("$ref");
        final String prefix = "#/components/parameters/";
        if (ref == null || !ref.isTextual()) {
            return parameter;
        }
        if (!ref.asText().startsWith(prefix) || root.get("components") == null
                || root.get("components").get("parameters") == null) {
            return null;
        }
        return root.get("components").get("parameters").get(
                ref.asText().substring(prefix.length()).replace("~1", "/").replace("~0", "~"));
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
