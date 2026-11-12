package io.github.mridang.codegen.generators;

import java.util.Locale;
import org.openapitools.codegen.utils.StringUtils;

/**
 * Centralizes all casing and naming conversions used by the
 * language codegens. Each variant encapsulates a single naming
 * strategy so that codegen classes can declare their conventions
 * as static fields instead of scattering {@code StringUtils}
 * calls throughout their methods.
 *
 * <p>Usage in a codegen:
 * <pre>
 * private static final NamingConvention VAR_CASING =
 *     NamingConvention.CAMEL_CASE;
 *
 * protected String applyVarNameCasing(String name) {
 *     return VAR_CASING.apply(name);
 * }
 * </pre>
 */
public enum NamingConvention {

    /**
     * Produces {@code camelCase} — the leading acronym lowercased.
     *
     * <p>Not simply {@code camelize(input, LOWERCASE_FIRST_LETTER)}: that
     * lowercases only the FIRST character, so a name beginning with an acronym
     * comes out with a stray capital run — {@code OIDCService} became
     * {@code oIDCService} and {@code SAMLService} became {@code sAMLService}.
     * The snake_case convention never had the problem because
     * {@code underscore()} understands acronym runs, which is why the
     * snake_case SDKs read correctly while the camelCase ones did not.
     *
     * <p>The whole leading run of capitals is lowercased instead, stopping
     * before the capital that starts the next word: {@code OIDCService} →
     * {@code oidcService}, {@code SAMLService} → {@code samlService},
     * {@code APIKey} → {@code apiKey}, a bare {@code OIDC} → {@code oidc}, and
     * an ordinary {@code ApiClient} → {@code apiClient} as before.
     */
    CAMEL_CASE {
        @Override
        public String apply(String input) {
            return lowercaseLeadingAcronym(StringUtils.camelize(input));
        }
    },

    /** Produces {@code PascalCase} — first letter uppercase. */
    PASCAL_CASE {
        @Override
        public String apply(String input) {
            return StringUtils.camelize(input);
        }
    },

    /** Produces {@code snake_case} — all lowercase with underscores. */
    SNAKE_CASE {
        @Override
        public String apply(String input) {
            return StringUtils.underscore(input);
        }
    },

    /** Produces {@code kebab-case} — all lowercase with hyphens. */
    KEBAB_CASE {
        @Override
        public String apply(String input) {
            return StringUtils.underscore(input).replace('_', '-');
        }
    },

    /** Produces {@code UPPER_SNAKE_CASE} — all uppercase with underscores. */
    UPPER_SNAKE_CASE {
        @Override
        public String apply(String input) {
            return StringUtils.underscore(input).toUpperCase(Locale.ROOT);
        }
    },

    /** Returns the input unchanged — no casing transformation. */
    IDENTITY {
        @Override
        public String apply(String input) {
            return input;
        }
    };

    /**
     * Applies this naming convention to the given input string.
     */
    public abstract String apply(String input);

    /**
     * Lowercases the leading run of capitals in a PascalCase identifier.
     *
     * <p>Where the run is followed by a lowercase letter, its LAST capital
     * begins the next word and is therefore preserved — in {@code OIDCService}
     * the run is {@code OIDCS} but the trailing {@code S} opens
     * {@code Service}, so only {@code OIDC} is folded down. A run reaching the
     * end of the string is folded whole, and a single leading capital takes the
     * ordinary path.
     *
     * @param pascal a PascalCase identifier
     * @return the identifier in camelCase
     */
    private static String lowercaseLeadingAcronym(String pascal) {
        if (pascal == null || pascal.isEmpty()) {
            return pascal;
        }
        final int length = pascal.length();
        int run = 0;
        while (run < length && Character.isUpperCase(pascal.charAt(run))) {
            run++;
        }
        if (run <= 1) {
            return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
        }
        final int foldTo = (run < length && Character.isLowerCase(pascal.charAt(run))) ? run - 1 : run;
        return pascal.substring(0, foldTo).toLowerCase(Locale.ROOT) + pascal.substring(foldTo);
    }
}
