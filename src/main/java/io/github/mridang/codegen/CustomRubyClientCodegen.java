package io.github.mridang.codegen;

import org.openapitools.codegen.languages.RubyClientCodegen;

import java.util.Locale;

@SuppressWarnings("unused")
public class CustomRubyClientCodegen extends RubyClientCodegen {

    @Override
    public String getName() {
        return "custom-ruby";
    }

    @Override
    public String getHelp() {
        return "Generates a custom Ruby client library.";
    }

    /**
     * Overrides the default model filename creation to produce a snake_case filename
     * compatible with Zeitwerk's handling of acronyms (e.g., "OIDC" becomes "o_i_d_c").
     *
     * @param name The name of the model.
     * @return The Zeitwerk-compatible underscored model filename.
     */
    @Override
    public String toModelFilename(String name) {
        return zeitwerkUnderscore(toModelName(name));
    }

    /**
     * Overrides the default API filename creation for consistency with the model naming.
     *
     * @param name The name of the api.
     * @return The Zeitwerk-compatible underscored api filename.
     */
    @Override
    public String toApiFilename(String name) {
        return zeitwerkUnderscore(super.toApiName(name));
    }

    /**
     * A custom underscoring method that correctly handles acronyms for Zeitwerk.
     * It inserts an underscore before any uppercase letter that is not at the start of the string.
     * Example: "AbstractOIDC" -> "abstract_o_i_d_c"
     * Example: "URL" -> "u_r_l"
     * Example: "OIDCConfig" -> "o_i_d_c_config"
     *
     * @param word The word to be underscored.
     * @return The fully underscored string in lowercase.
     */
    private String zeitwerkUnderscore(String word) {
        if (word == null || word.trim().isEmpty()) {
            return "";
        }
        // Using a negative lookbehind `(?<!^)` to add an underscore before any uppercase letter `([A-Z])`
        // that is not at the beginning of the string. This correctly splits acronyms.
        String result = word.replaceAll("(?<!^)([A-Z])", "_$1");

        return result.replace('-', '_').toLowerCase(Locale.ROOT);
    }
}
