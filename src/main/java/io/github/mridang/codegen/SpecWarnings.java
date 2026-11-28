package io.github.mridang.codegen;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import javax.annotation.Nullable;
import org.slf4j.Logger;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The warning pass of the shared normaliser. It lists every {@code $ref} that does not resolve
 * inside the document, and every construct the generators do not realise (callbacks, webhooks,
 * Link Objects and the {@code xml} object), and every JSON Schema dialect other than the
 * OpenAPI dialect that {@code jsonSchemaDialect} or a schema's {@code $schema} selects. Each warning carries the JSON pointer of the place
 * where it was found, so the position can be read straight from the specification.
 * <p>
 * Warnings are only logged by default, so the run stays lenient. When the caller asks for
 * strictness, any warning fails the run instead.
 */
public final class SpecWarnings {

    /**
     * The system property that makes every warning fail the run. It is read as a boolean, so
     * {@code -DstrictSpec=true} turns it on and any other value, or no value, leaves it off.
     */
    public static final String STRICT_PROPERTY = "strictSpec";

    /**
     * Keys whose value is a map of names rather than a map of keywords. The keys of such a map
     * are user-chosen names (a property called {@code links} is not a Link Object), so the
     * keyword checks skip them.
     */
    private static final Set<String> NAME_KEYED = Set.of(
        "paths", "webhooks", "callbacks", "components", "schemas", "responses", "parameters",
        "examples", "requestBodies", "headers", "links", "content", "encoding", "securitySchemes",
        "properties", "patternProperties", "$defs", "definitions", "dependentSchemas", "variables",
        "scopes");

    /**
     * Keywords the generators ignore, with the reason given to the user. A keyword is only
     * matched where it is a keyword, never where it is a name.
     */
    private static final Map<String, String> UNSUPPORTED = Map.of(
        "webhooks", "webhooks are not supported and are ignored",
        "callbacks", "callbacks are not supported and are ignored",
        "links", "Link Objects are not supported and are ignored",
        "xml", "the xml object is not supported and is ignored");

    /**
     * The dialects the schemas are read in: the OpenAPI dialects of 3.1 and 3.2 and plain JSON
     * Schema 2020-12, which the OpenAPI dialect extends. A dialect is matched by prefix.
     */
    private static final List<String> SUPPORTED_DIALECTS = List.of(
        "https://spec.openapis.org/oas/3.1/dialect/",
        "https://spec.openapis.org/oas/3.2/dialect/",
        "https://json-schema.org/draft/2020-12/schema");

    private SpecWarnings() {
    }

    /**
     * One finding of the pass.
     */
    public static final class Warning {

        private final String pointer;
        private final String message;

        Warning(String pointer, String message) {
            this.pointer = pointer;
            this.message = message;
        }

        /**
         * Returns the position of the problem as a JSON pointer, such as
         * {@code #/paths/~1pets/get/responses/200}.
         *
         * @return the JSON pointer of the place where the problem was found.
         */
        public String getPointer() {
            return pointer;
        }

        /**
         * Returns what is wrong at that place.
         *
         * @return the description of the problem.
         */
        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return pointer + ": " + message;
        }
    }

    /**
     * Lists every warning in the specification without logging or failing.
     *
     * @param openAPI The specification to inspect.
     * @return the warnings in document order, empty when the specification is clean.
     */
    public static List<Warning> collect(OpenAPI openAPI) {
        Map<String, Object> root = toMap(openAPI);
        List<Warning> warnings = new ArrayList<>();
        loaderWarnings(openAPI, warnings);
        checkDialect(openAPI.getJsonSchemaDialect(), "#/jsonSchemaDialect", warnings);
        checkPathTemplates(openAPI, warnings);
        checkServers(openAPI.getServers(), "#/servers", warnings);
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                String pointer = child("#/paths", OpenApi32ParserExtension.resourcePath(entry.getKey()));
                if (!isRelocated(entry.getKey())) {
                    checkServers(entry.getValue().getServers(), pointer + "/servers", warnings);
                }
                for (Map.Entry<String, Operation> op : operations(entry.getKey(), entry.getValue()).entrySet()) {
                    checkServers(op.getValue().getServers(), pointer + "/" + op.getKey() + "/servers", warnings);
                }
            }
        }
        walk(root, root, "#", false, warnings);
        walkDialects(Json31.mapper().convertValue(openAPI, Map.class), "#", false, warnings);
        return warnings;
    }

    /**
     * Whether the generators read schemas of the given dialect as written. Any other dialect is
     * read as the OpenAPI dialect, which is what a warning says.
     *
     * @param dialect The URI of the dialect.
     * @return true for the OpenAPI 3.1 and 3.2 dialects and JSON Schema 2020-12.
     */
    public static boolean isSupportedDialect(String dialect) {
        String trimmed = dialect.trim();
        for (String supported : SUPPORTED_DIALECTS) {
            if (trimmed.startsWith(supported)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A path literal character of the OpenAPI 3.2 path-template grammar ({@code pchar} of RFC 3986
     * without {@code %}, which is checked as a percent-encoded triplet).
     */
    private static final String PCHAR = "-._~!$&'()*+,;=:@";

    /** Whether a path key is one the 3.2 loader made for a QUERY or custom-method operation. */
    private static boolean isRelocated(String key) {
        return !OpenApi32ParserExtension.resourcePath(key).equals(key);
    }

    /**
     * The operations of a path item keyed by the pointer segment of the field that declares them:
     * the method field, {@code query}, or {@code additionalOperations/<METHOD>} for an operation
     * the 3.2 loader moved into a path item of its own.
     */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "HTTP method names are ASCII")
    private static Map<String, Operation> operations(String key, PathItem item) {
        Map<String, Operation> operations = new java.util.LinkedHashMap<>();
        for (Map.Entry<PathItem.HttpMethod, Operation> op : item.readOperationsMap().entrySet()) {
            String declared = OpenApi32ParserExtension.declaredMethod(op.getValue());
            if (declared == null) {
                operations.put(op.getKey().name().toLowerCase(java.util.Locale.ROOT), op.getValue());
            } else if (isRelocated(key)) {
                operations.put("QUERY".equals(declared) ? "query"
                    : "additionalOperations/" + declared, op.getValue());
            }
        }
        return operations;
    }

    /**
     * Checks every path against the path-templating grammar of OpenAPI 3.2: a path starts with
     * {@code /}, its segments are not empty, a literal is made of RFC 3986 path characters or
     * percent-encoded octets, every {@code {name}} is closed, not nested and not repeated, and
     * each operation of the path declares a path parameter for every name (D162).
     */
    private static void checkPathTemplates(OpenAPI openAPI, List<Warning> warnings) {
        if (openAPI.getPaths() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = OpenApi32ParserExtension.resourcePath(entry.getKey());
            String pointer = child("#/paths", path);
            if (path.startsWith("x-")) {
                continue;
            }
            List<String> names = new ArrayList<>();
            String problem = templateProblem(path, names);
            if (problem != null) {
                if (!isRelocated(entry.getKey())) {
                    warnings.add(new Warning(pointer, "the path template '" + path
                        + "' is not valid: " + problem));
                }
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, Operation> op : operations(entry.getKey(), entry.getValue()).entrySet()) {
                Set<String> declared = new java.util.HashSet<>();
                addPathParameters(openAPI, entry.getValue().getParameters(), declared);
                addPathParameters(openAPI, op.getValue().getParameters(), declared);
                for (String name : names) {
                    if (!declared.contains(name)) {
                        warnings.add(new Warning(pointer + "/" + op.getKey(), "the path template"
                            + " expression '{" + name + "}' has no path parameter named '" + name
                            + "'"));
                    }
                }
            }
        }
    }

    /**
     * Returns why a path is not a valid OpenAPI 3.2 path template, or null when it is one; the
     * names of its template expressions are added to {@code names} in order.
     */
    @Nullable
    static String templateProblem(String path, List<String> names) {
        if (!path.startsWith("/")) {
            return "it must start with '/'";
        }
        String[] segments = path.substring(1).split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                if (i == segments.length - 1) {
                    continue;
                }
                return "it has an empty segment";
            }
            int at = 0;
            while (at < segment.length()) {
                char c = segment.charAt(at);
                if (c == '{') {
                    int close = segment.indexOf('}', at);
                    int nested = segment.indexOf('{', at + 1);
                    if (close < 0 || (nested >= 0 && nested < close)) {
                        return "the expression at '" + segment.substring(at) + "' is not closed";
                    }
                    String name = segment.substring(at + 1, close);
                    if (name.isEmpty()) {
                        return "it has an empty expression '{}'";
                    }
                    if (names.contains(name)) {
                        return "the expression '{" + name + "}' appears more than once";
                    }
                    names.add(name);
                    at = close + 1;
                } else if (c == '}') {
                    return "it has a '}' that closes no expression";
                } else if (c == '%') {
                    if (at + 2 >= segment.length() || !isHex(segment.charAt(at + 1))
                        || !isHex(segment.charAt(at + 2))) {
                        return "'%' must start a percent-encoded octet";
                    }
                    at += 3;
                } else if (isAsciiAlphanumeric(c) || PCHAR.indexOf(c) >= 0) {
                    at++;
                } else {
                    return "the character '" + c + "' must be percent-encoded";
                }
            }
        }
        return null;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAsciiAlphanumeric(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** Adds the names of the path parameters of a list, following local references. */
    private static void addPathParameters(OpenAPI openAPI,
                                          @Nullable List<io.swagger.v3.oas.models.parameters.Parameter> parameters,
                                          Set<String> names) {
        if (parameters == null) {
            return;
        }
        for (io.swagger.v3.oas.models.parameters.Parameter parameter : parameters) {
            io.swagger.v3.oas.models.parameters.Parameter resolved = parameter;
            String ref = parameter.get$ref();
            String prefix = "#/components/parameters/";
            if (ref != null && ref.startsWith(prefix) && openAPI.getComponents() != null
                && openAPI.getComponents().getParameters() != null) {
                resolved = openAPI.getComponents().getParameters().get(ref.substring(prefix.length()));
            }
            if (resolved != null && "path".equals(resolved.getIn()) && resolved.getName() != null) {
                names.add(resolved.getName());
            }
        }
    }

    /**
     * Checks server URLs against the server-URL grammar of OpenAPI 3.2: no space, control
     * character, {@code "}, {@code <}, {@code >}, {@code \}, {@code ^}, {@code `} or {@code |},
     * a {@code %} only as a percent-encoded octet, every {@code {name}} closed, not nested, not
     * repeated and declared in {@code variables}; and a variable's {@code enum} is not empty and
     * holds its {@code default} (D162).
     */
    private static void checkServers(@Nullable List<io.swagger.v3.oas.models.servers.Server> servers,
                                     String pointer, List<Warning> warnings) {
        if (servers == null) {
            return;
        }
        for (int i = 0; i < servers.size(); i++) {
            io.swagger.v3.oas.models.servers.Server server = servers.get(i);
            if (server == null || server.getUrl() == null) {
                continue;
            }
            String at = pointer + "/" + i;
            List<String> names = new ArrayList<>();
            String problem = serverUrlProblem(server.getUrl(), names);
            if (problem != null) {
                warnings.add(new Warning(at + "/url", "the server URL '" + server.getUrl()
                    + "' is not valid: " + problem));
                continue;
            }
            Map<String, io.swagger.v3.oas.models.servers.ServerVariable> variables =
                server.getVariables() == null ? Map.of() : server.getVariables();
            for (String name : names) {
                if (!variables.containsKey(name)) {
                    warnings.add(new Warning(at + "/url", "the server variable '{" + name
                        + "}' is not declared in variables"));
                }
            }
            for (Map.Entry<String, io.swagger.v3.oas.models.servers.ServerVariable> variable
                : variables.entrySet()) {
                List<String> values = variable.getValue() == null ? null : variable.getValue().getEnum();
                String where = child(at + "/variables", variable.getKey());
                if (values != null && values.isEmpty()) {
                    warnings.add(new Warning(where + "/enum", "the enum of a server variable must"
                        + " not be empty"));
                } else if (values != null && !values.contains(variable.getValue().getDefault())) {
                    warnings.add(new Warning(where + "/default", "the default '"
                        + variable.getValue().getDefault() + "' is not one of the enum values"));
                }
            }
        }
    }

    /**
     * Returns why a server URL is not a valid OpenAPI 3.2 server URL template, or null when it is
     * one; the names of its variables are added to {@code names} in order.
     */
    @Nullable
    static String serverUrlProblem(String url, List<String> names) {
        if (url.isEmpty()) {
            return "it is empty";
        }
        int at = 0;
        while (at < url.length()) {
            char c = url.charAt(at);
            if (c == '{') {
                int close = url.indexOf('}', at);
                int nested = url.indexOf('{', at + 1);
                if (close < 0 || (nested >= 0 && nested < close)) {
                    return "the variable at '" + url.substring(at) + "' is not closed";
                }
                String name = url.substring(at + 1, close);
                if (name.isEmpty()) {
                    return "it has an empty variable '{}'";
                }
                if (names.contains(name)) {
                    return "the variable '{" + name + "}' appears more than once";
                }
                names.add(name);
                at = close + 1;
            } else if (c == '}') {
                return "it has a '}' that closes no variable";
            } else if (c == '%') {
                if (at + 2 >= url.length() || !isHex(url.charAt(at + 1)) || !isHex(url.charAt(at + 2))) {
                    return "'%' must start a percent-encoded octet";
                }
                at += 3;
            } else if (c <= 0x20 || c == 0x7F || "\"<>\\^`|".indexOf(c) >= 0) {
                return "the character '" + (c <= 0x20 || c == 0x7F
                    ? String.format(java.util.Locale.ROOT, "U+%04X", (int) c)
                    : String.valueOf(c)) + "' is not allowed";
            } else {
                at++;
            }
        }
        return null;
    }

    /** Lists what the OpenAPI 3.2 loader left out of the document, with the pointer it gave. */
    private static void loaderWarnings(OpenAPI openAPI, List<Warning> warnings) {
        Object carried = openAPI.getExtensions() == null
            ? null
            : openAPI.getExtensions().get(OpenApi32ParserExtension.LOADER_WARNINGS);
        if (!(carried instanceof List)) {
            return;
        }
        for (Object entry : (List<?>) carried) {
            if (entry instanceof Map) {
                Object pointer = ((Map<?, ?>) entry).get("pointer");
                Object message = ((Map<?, ?>) entry).get("message");
                if (pointer instanceof String && message instanceof String) {
                    warnings.add(new Warning((String) pointer, (String) message));
                }
            }
        }
    }

    private static void checkDialect(@Nullable Object dialect, String pointer, List<Warning> warnings) {
        if (dialect instanceof String && !isSupportedDialect((String) dialect)) {
            warnings.add(new Warning(pointer, "the JSON Schema dialect '" + dialect
                + "' is not supported; the schemas are read as the OpenAPI dialect"
                + " (JSON Schema 2020-12)"));
        }
    }

    /** Lists the {@code $schema} of every schema; the 3.0 mapper of {@link #toMap} drops it. */
    @SuppressWarnings("unchecked")
    private static void walkDialects(Object node, String pointer, boolean nameKeyed,
                                     List<Warning> warnings) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (!nameKeyed) {
                checkDialect(map.get("$schema"), child(pointer, "$schema"), warnings);
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                walkDialects(entry.getValue(), child(pointer, entry.getKey()),
                    !nameKeyed && NAME_KEYED.contains(entry.getKey()), warnings);
            }
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                walkDialects(list.get(i), pointer + "/" + i, false, warnings);
            }
        }
    }

    /**
     * Logs every warning in the specification. When {@code strict} is set and there is at least
     * one warning, the run fails after all of them have been logged.
     *
     * @param openAPI The specification to inspect.
     * @param strict  Whether a warning fails the run.
     * @param logger  The logger that receives the warnings.
     * @throws IllegalStateException when {@code strict} is set and the specification has warnings.
     */
    public static void check(OpenAPI openAPI, boolean strict, Logger logger) {
        List<Warning> warnings = collect(openAPI);
        for (Warning warning : warnings) {
            logger.warn("Spec warning at {}: {}", warning.getPointer(), warning.getMessage());
        }
        if (strict && !warnings.isEmpty()) {
            throw new IllegalStateException("The specification has " + warnings.size()
                + " warning(s) and " + STRICT_PROPERTY + " is set: " + warnings.get(0));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(OpenAPI openAPI) {
        return Json.mapper().convertValue(openAPI, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Map<String, Object> root, Object node, String pointer,
                             boolean nameKeyed, List<Warning> warnings) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (!nameKeyed) {
                Object ref = map.get("$ref");
                if (ref instanceof String) {
                    checkRef(root, (String) ref, pointer, warnings);
                }
                for (String key : map.keySet()) {
                    if (UNSUPPORTED.containsKey(key)) {
                        warnings.add(new Warning(child(pointer, key), UNSUPPORTED.get(key)));
                    }
                }
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                boolean childNameKeyed = !nameKeyed && NAME_KEYED.contains(entry.getKey());
                walk(root, entry.getValue(), child(pointer, entry.getKey()), childNameKeyed, warnings);
            }
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                walk(root, list.get(i), pointer + "/" + i, false, warnings);
            }
        }
    }

    private static void checkRef(Map<String, Object> root, String ref, String pointer,
                                 List<Warning> warnings) {
        if (!ref.startsWith("#")) {
            warnings.add(new Warning(pointer,
                "$ref '" + ref + "' is not a local JSON pointer and is not resolved"));
        } else if (!resolves(root, ref.substring(1))) {
            warnings.add(new Warning(pointer, "dangling $ref '" + ref + "'"));
        }
    }

    private static boolean resolves(Object root, String fragment) {
        if (fragment.isEmpty()) {
            return true;
        }
        if (!fragment.startsWith("/")) {
            return false;
        }
        Object current = root;
        for (String raw : fragment.substring(1).split("/", -1)) {
            String segment = unescape(raw);
            if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (!map.containsKey(segment)) {
                    return false;
                }
                current = map.get(segment);
            } else if (current instanceof List) {
                List<?> list = (List<?>) current;
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (index < 0 || index >= list.size()) {
                    return false;
                }
                current = list.get(index);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Reverses the URI fragment encoding and the JSON pointer escapes of one reference segment.
     */
    private static String unescape(String segment) {
        String decoded = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
        return decoded.replace("~1", "/").replace("~0", "~");
    }

    private static String child(String pointer, String key) {
        return pointer + "/" + key.replace("~", "~0").replace("/", "~1");
    }
}
