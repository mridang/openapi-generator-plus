package io.github.mridang.codegen.generators;

import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;

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

    /** Produces {@code camelCase} — first letter lowercase. */
    CAMEL_CASE {
        @Override
        public String apply(String input) {
            return StringUtils.camelize(input, LOWERCASE_FIRST_LETTER);
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
}
