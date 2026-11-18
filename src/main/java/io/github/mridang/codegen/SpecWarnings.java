package io.github.mridang.codegen;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
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
 * Link Objects and the {@code xml} object). Each warning carries the JSON pointer of the place
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
        walk(root, root, "#", false, warnings);
        return warnings;
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
