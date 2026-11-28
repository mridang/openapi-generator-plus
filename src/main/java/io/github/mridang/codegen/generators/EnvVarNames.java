package io.github.mridang.codegen.generators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Names the environment variable each credential field of each security scheme may default
 * from (F862). The names are computed once, here, so all twelve languages read the same one.
 *
 * <p>A name is {@code {PREFIX}_{FIELD}} in upper snake case ({@code PETSTORE_TOKEN}). When two
 * schemes have a field of the same name, every one of them is qualified with its scheme id
 * ({@code PETSTORE_SERVICE_TOKEN_TOKEN}) so that no scheme silently reads another's secret and
 * adding a scheme never renames an existing variable of another field. Names that still collide
 * after that (two scheme ids that fold to the same upper snake text) get a numeric suffix, in
 * scheme id order.
 */
public final class EnvVarNames {

    private EnvVarNames() {}

    /**
     * Folds text to upper snake case made only of {@code A-Z}, {@code 0-9} and {@code _}: a
     * camelCase boundary becomes an underscore, every other run of characters outside
     * {@code A-Za-z0-9} becomes one underscore, and leading and trailing underscores are
     * dropped.
     *
     * @param text any text, such as a package name, a scheme id or a field name
     * @return the upper snake text, or an empty string when nothing alphanumeric is left
     */
    public static String upperSnake(String text) {
        final StringBuilder sb = new StringBuilder();
        boolean pendingSeparator = false;
        char previous = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            final boolean ascii = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (!ascii) {
                pendingSeparator = sb.length() > 0;
                previous = 0;
                continue;
            }
            final boolean camelBoundary = Character.isUpperCase(c)
                    && (Character.isLowerCase(previous) || Character.isDigit(previous)
                            || (Character.isUpperCase(previous)
                                    && i + 1 < text.length()
                                    && Character.isLowerCase(text.charAt(i + 1))));
            if (sb.length() > 0 && (pendingSeparator || camelBoundary)) {
                sb.append('_');
            }
            pendingSeparator = false;
            sb.append(Character.toUpperCase(c));
            previous = c;
        }
        return sb.toString();
    }

    /**
     * Returns the variable prefix: {@code upperSnake(candidate)}, or {@code API} when that is
     * empty, with a leading digit guarded by {@code API_} because a variable name may not start
     * with one.
     *
     * @param candidate the configured prefix, the package name or the client name
     * @return a prefix that can start an environment variable name
     */
    public static String prefix(String candidate) {
        final String folded = upperSnake(candidate);
        if (folded.isEmpty()) {
            return "API";
        }
        return Character.isDigit(folded.charAt(0)) ? "API_" + folded : folded;
    }

    /**
     * Computes the environment variable of every credential field of every scheme.
     *
     * @param prefix the prefix from {@link #prefix(String)}
     * @param fieldsByScheme the credential fields of each scheme id, in constructor order
     * @return one entry per scheme field, in scheme id order then field order, each a map of
     *     {@code schemeId}, {@code field} (as the scheme's constructor names it),
     *     {@code fieldSnake} (upper snake) and {@code envName}
     */
    public static List<Map<String, Object>> compute(
            String prefix, Map<String, List<String>> fieldsByScheme) {
        final Map<String, List<String>> sorted = new TreeMap<>(fieldsByScheme);
        final Map<String, Integer> fieldUses = new HashMap<>();
        for (final List<String> fields : sorted.values()) {
            for (final String field : new HashSet<>(fields)) {
                fieldUses.merge(upperSnake(field), 1, Integer::sum);
            }
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        final Set<String> taken = new HashSet<>();
        for (final Map.Entry<String, List<String>> scheme : sorted.entrySet()) {
            for (final String field : scheme.getValue()) {
                final String fieldSnake = upperSnake(field);
                final String schemeSnake = upperSnake(scheme.getKey());
                String name = prefix + "_" + fieldSnake;
                if (fieldUses.getOrDefault(fieldSnake, 0) > 1 && !schemeSnake.isEmpty()) {
                    name = prefix + "_" + schemeSnake + "_" + fieldSnake;
                }
                final String base = name;
                for (int n = 2; !taken.add(name); n++) {
                    name = base + "_" + n;
                }
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("schemeId", scheme.getKey());
                entry.put("field", field);
                entry.put("fieldSnake", fieldSnake);
                entry.put("envName", name);
                out.add(entry);
            }
        }
        return out;
    }
}
