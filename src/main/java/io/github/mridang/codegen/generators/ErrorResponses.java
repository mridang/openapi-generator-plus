package io.github.mridang.codegen.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Turns the non-success responses an operation declares into the data the
 * error templates need: which status class each response belongs to, which
 * error schema (if any) it carries, and one list of distinct error schemas for
 * the whole specification with the status class each one subclasses.
 *
 * <p>The class is pure: it knows nothing about a particular language except
 * the suffix ({@code Error} or {@code Exception}) it is asked to append, so
 * the same rules produce the same hierarchy in all twelve SDKs.
 */
public final class ErrorResponses {

    /** Stem of the base type every HTTP error derives from. */
    static final String API = "Api";

    /** Stem of the family of 4xx errors. */
    static final String CLIENT = "Client";

    /** Stem of the family of 5xx errors. */
    static final String SERVER = "Server";

    /** Stem of the 500 type, whose name already contains the word Error. */
    static final String INTERNAL_SERVER_ERROR = "InternalServerError";

    /** Status codes that have a type of their own, in ascending order, with their stem. */
    private static final Map<Integer, String> STATUS_STEMS = new LinkedHashMap<>();

    static {
        STATUS_STEMS.put(400, "BadRequest");
        STATUS_STEMS.put(401, "Unauthorized");
        STATUS_STEMS.put(402, "PaymentRequired");
        STATUS_STEMS.put(403, "Forbidden");
        STATUS_STEMS.put(404, "NotFound");
        STATUS_STEMS.put(405, "MethodNotAllowed");
        STATUS_STEMS.put(408, "RequestTimeout");
        STATUS_STEMS.put(409, "Conflict");
        STATUS_STEMS.put(410, "Gone");
        STATUS_STEMS.put(413, "PayloadTooLarge");
        STATUS_STEMS.put(415, "UnsupportedMediaType");
        STATUS_STEMS.put(422, "UnprocessableEntity");
        STATUS_STEMS.put(423, "Locked");
        STATUS_STEMS.put(429, "TooManyRequests");
        STATUS_STEMS.put(500, INTERNAL_SERVER_ERROR);
        STATUS_STEMS.put(501, "NotImplemented");
        STATUS_STEMS.put(502, "BadGateway");
        STATUS_STEMS.put(503, "ServiceUnavailable");
        STATUS_STEMS.put(504, "GatewayTimeout");
    }

    private ErrorResponses() {}

    /** One declared error response: a status key and the model it carries, if any. */
    public static final class Declared {
        private final String status;
        @Nullable private final String modelName;

        /**
         * Creates a declared response.
         *
         * @param status {@code 404}, {@code 4XX}, {@code 5XX} or {@code default}
         * @param modelName the language's model class the body decodes to, or null for none
         */
        public Declared(String status, @Nullable String modelName) {
            this.status = normaliseStatus(status);
            this.modelName = modelName == null || modelName.isEmpty() ? null : modelName;
        }
    }

    /**
     * Whether a response key is an error status: 4xx, 5xx, a 4XX or 5XX range,
     * or the default response. A {@code 0} code is how the generator spells the
     * default response.
     *
     * @param status the response key
     * @return true for an error response
     */
    public static boolean isErrorStatus(String status) {
        final String s = normaliseStatus(status);
        return "default".equals(s) || s.matches("[45]\\d\\d|[45]XX");
    }

    static String normaliseStatus(String status) {
        final String s = status.trim().toUpperCase(Locale.ROOT);
        if ("DEFAULT".equals(s) || "0".equals(s)) {
            return "default";
        }
        return s;
    }

    /**
     * The stem of the status class a response key belongs to: a status with its
     * own type gives that type, any other 4xx or 4XX gives {@code Client}, any
     * other 5xx or 5XX gives {@code Server}, and default gives {@code Api}.
     *
     * @param status the response key
     * @return the status class stem
     */
    public static String statusStem(String status) {
        final String s = normaliseStatus(status);
        if (s.matches("\\d\\d\\d")) {
            final int code = Integer.parseInt(s);
            final String stem = STATUS_STEMS.get(code);
            if (stem != null) {
                return stem;
            }
            if (code >= 400 && code < 500) {
                return CLIENT;
            }
            if (code >= 500 && code < 600) {
                return SERVER;
            }
            return API;
        }
        if ("4XX".equals(s)) {
            return CLIENT;
        }
        if ("5XX".equals(s)) {
            return SERVER;
        }
        return API;
    }

    /**
     * The stem of the type a status class derives from.
     *
     * @param stem a status class stem
     * @return {@code Client} or {@code Server} for a specific status, {@code Api} for those
     */
    public static String parentStem(String stem) {
        if (API.equals(stem)) {
            return API;
        }
        if (CLIENT.equals(stem) || SERVER.equals(stem)) {
            return API;
        }
        for (final Map.Entry<Integer, String> e : STATUS_STEMS.entrySet()) {
            if (e.getValue().equals(stem)) {
                return e.getKey() < 500 ? CLIENT : SERVER;
            }
        }
        return API;
    }

    /**
     * The lowest stem every one of the given stems derives from, so that a
     * schema declared under 409 and 423 is a {@code Client} error and one
     * declared under a 4XX and a 5XX is an {@code Api} error.
     *
     * @param stems the stems of every status a schema is declared under
     * @return the common ancestor stem
     */
    public static String commonStem(Collection<String> stems) {
        String common = null;
        for (final String stem : stems) {
            if (common == null) {
                common = stem;
                continue;
            }
            common = commonOfTwo(common, stem);
        }
        return common == null ? API : common;
    }

    private static String commonOfTwo(String a, String b) {
        if (a.equals(b)) {
            return a;
        }
        final List<String> chain = new ArrayList<>();
        for (String s = a; ; s = parentStem(s)) {
            chain.add(s);
            if (API.equals(s)) {
                break;
            }
        }
        for (String s = b; ; s = parentStem(s)) {
            if (chain.contains(s)) {
                return s;
            }
            if (API.equals(s)) {
                return API;
            }
        }
    }

    /**
     * The full class name of a status class in a language.
     *
     * @param stem the status class stem
     * @param suffix the language's error suffix, {@code Error} or {@code Exception}
     * @return for example {@code NotFoundException}, or {@code InternalServerError} when the
     *     suffix is {@code Error}
     */
    public static String statusClassName(String stem, String suffix) {
        if (INTERNAL_SERVER_ERROR.equals(stem) && "Error".equals(suffix)) {
            return INTERNAL_SERVER_ERROR;
        }
        return stem + suffix;
    }

    /**
     * The class name for the error carrying a given schema: the model name with
     * the language's error suffix, so it can never collide with the model.
     *
     * @param modelName the language's model class name
     * @param suffix the language's error suffix
     * @return for example {@code ProjectNotFoundException}
     */
    public static String schemaClassName(String modelName, String suffix) {
        return modelName + suffix;
    }

    /**
     * Builds the per-operation rows. Order follows the declared order, except a
     * default response always comes last; a status declared twice keeps its
     * first schema.
     *
     * @param declared the non-success responses the operation declares
     * @param suffix the language's error suffix
     * @return one row per distinct status, see the keys in {@link #rowOf}
     */
    public static List<Map<String, Object>> operationRows(
            List<Declared> declared, String suffix) {
        return operationRows(declared, suffix, stem -> statusClassName(stem, suffix));
    }

    /**
     * As {@link #operationRows(List, String)} with the status class names chosen by the
     * language, for the one that cannot use a stem as it is (C# has its own
     * {@code NotImplementedException}).
     *
     * @param declared the non-success responses the operation declares
     * @param suffix the language's error suffix
     * @param statusNamer maps a status class stem to the language's class name
     * @return one row per distinct status
     */
    public static List<Map<String, Object>> operationRows(
            List<Declared> declared, String suffix, Function<String, String> statusNamer) {
        final Map<String, Declared> byStatus = new LinkedHashMap<>();
        Declared defaultResponse = null;
        for (final Declared d : declared) {
            if (!isErrorStatus(d.status)) {
                continue;
            }
            if ("default".equals(d.status)) {
                if (defaultResponse == null) {
                    defaultResponse = d;
                }
                continue;
            }
            byStatus.putIfAbsent(d.status, d);
        }
        if (defaultResponse != null) {
            byStatus.put("default", defaultResponse);
        }
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Declared d : byStatus.values()) {
            rows.add(rowOf(d, suffix, statusNamer));
        }
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).put("isFirst", i == 0);
            rows.get(i).put("isLast", i == rows.size() - 1);
        }
        return rows;
    }

    /**
     * One operation row: {@code status}, {@code statusClass} (the stem),
     * {@code statusClassName}, {@code schemaName} (null without a schema),
     * {@code schemaClassName}, {@code hasSchema}, {@code isDefault} and
     * {@code isRange}.
     */
    private static Map<String, Object> rowOf(
            Declared d, String suffix, Function<String, String> statusNamer) {
        final String stem = statusStem(d.status);
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", d.status);
        row.put("statusClass", stem);
        row.put("statusClassName", statusNamer.apply(stem));
        row.put("schemaName", d.modelName);
        row.put("schemaClassName", d.modelName == null ? null : schemaClassName(d.modelName, suffix));
        row.put("hasSchema", d.modelName != null);
        row.put("isDefault", "default".equals(d.status));
        row.put("isRange", d.status.endsWith("XX"));
        return row;
    }

    /**
     * A concrete status a response declared under the given key can arrive with.
     *
     * @param status the response key
     * @return the status itself, 400 for a 4XX range, 500 for a 5XX range or the default
     */
    static int sampleStatus(String status) {
        if (status.matches("\\d\\d\\d")) {
            return Integer.parseInt(status);
        }
        return "5XX".equals(status) ? 500 : 400;
    }

    /**
     * Folds the rows of every operation into the specification-level list of
     * distinct error schemas, in the order they are first seen. Each entry has
     * {@code schemaName}, {@code className}, {@code parentStem},
     * {@code parentClassName}, {@code parentIsFamily}, {@code parentIsApi},
     * {@code parentIsClient}, {@code parentIsServer}, {@code parentUnderClient} (the parent is
     * a specific 4xx status class), {@code parentUnderServer} (a specific 5xx status class),
     * {@code statuses} and {@code hasMultipleStatuses}.
     *
     * @param operations the rows of every operation, from {@link #operationRows}
     * @param suffix the language's error suffix
     * @return the distinct error schemas
     */
    public static List<Map<String, Object>> distinctSchemas(
            List<List<Map<String, Object>>> operations, String suffix) {
        return distinctSchemas(operations, suffix, stem -> statusClassName(stem, suffix));
    }

    /**
     * As {@link #distinctSchemas(List, String)} with the status class names chosen by the
     * language.
     *
     * @param operations the rows of every operation
     * @param suffix the language's error suffix
     * @param statusNamer maps a status class stem to the language's class name
     * @return the distinct error schemas
     */
    public static List<Map<String, Object>> distinctSchemas(
            List<List<Map<String, Object>>> operations,
            String suffix,
            Function<String, String> statusNamer) {
        final Map<String, Set<String>> statuses = new LinkedHashMap<>();
        for (final List<Map<String, Object>> rows : operations) {
            for (final Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("hasSchema"))) {
                    statuses.computeIfAbsent(
                                    (String) row.get("schemaName"), k -> new LinkedHashSet<>())
                            .add((String) row.get("status"));
                }
            }
        }
        final List<Map<String, Object>> schemas = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> e : statuses.entrySet()) {
            final List<String> stems = new ArrayList<>();
            for (final String status : e.getValue()) {
                stems.add(statusStem(status));
            }
            final String parent = commonStem(stems);
            final Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("schemaName", e.getKey());
            schema.put("className", schemaClassName(e.getKey(), suffix));
            schema.put("parentStem", parent);
            schema.put("parentClassName", statusNamer.apply(parent));
            schema.put("parentIsFamily", API.equals(parent) || CLIENT.equals(parent) || SERVER.equals(parent));
            schema.put("parentIsApi", API.equals(parent));
            schema.put("parentIsClient", CLIENT.equals(parent));
            schema.put("parentIsServer", SERVER.equals(parent));
            schema.put("parentUnderClient", CLIENT.equals(parentStem(parent)));
            schema.put("parentUnderServer", SERVER.equals(parentStem(parent)));
            schema.put("statuses", new ArrayList<>(e.getValue()));
            schema.put("hasMultipleStatuses", e.getValue().size() > 1);
            schema.put("sampleStatus", sampleStatus(e.getValue().iterator().next()));
            schemas.add(schema);
        }
        for (int i = 0; i < schemas.size(); i++) {
            schemas.get(i).put("isFirst", i == 0);
            schemas.get(i).put("isLast", i == schemas.size() - 1);
        }
        return schemas;
    }
}
