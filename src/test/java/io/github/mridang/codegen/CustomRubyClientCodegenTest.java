package io.github.mridang.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomRubyClientCodegenTest {

    @Test
    @DisplayName("Should convert a simple class name to a correct snake_case model filename")
    void toModelFilename_SimpleName() {
        CustomRubyClientCodegen codegen = new CustomRubyClientCodegen();
        String input = "SimpleTest";
        String expected = "simple_test";
        String actual = codegen.toModelFilename(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Should correctly underscore acronyms like OIDC at the end of a model name")
    void toModelFilename_AcronymAtEnd() {
        CustomRubyClientCodegen codegen = new CustomRubyClientCodegen();
        String input = "AbstractOIDC";
        String expected = "abstract_o_i_d_c";
        String actual = codegen.toModelFilename(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Should correctly underscore acronyms like URL as a whole model name")
    void toModelFilename_FullAcronym() {
        CustomRubyClientCodegen codegen = new CustomRubyClientCodegen();
        String input = "URL";
        String expected = "u_r_l";
        String actual = codegen.toModelFilename(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Should correctly underscore acronyms at the start of a model name")
    void toApiFilename_AcronymAtStart() {
        CustomRubyClientCodegen codegen = new CustomRubyClientCodegen();
        String input = "OIDCConfig";
        // Corrected expectation for Zeitwerk compatibility.
        String expected = "o_i_d_c_config_api";
        String actual = codegen.toApiFilename(input);
        assertEquals(expected, actual);
    }
}
