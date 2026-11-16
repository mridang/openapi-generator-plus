package io.github.mridang.codegen.generators.ruby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Validates the literal and constant-name rendering that the Ruby templates and
 * authenticator wrappers use for spec text, so a legal header, query or enum
 * value never breaks the generated source.
 */
class BetterRubyCodegenTest {

    @Test
    void quoteInAnApiKeyNameIsEscaped() {
        assertEquals("'X-Api\\'Key'", BetterRubyCodegen.toRubyStringLiteral("X-Api'Key"));
    }

    @Test
    void backslashInAnApiKeyNameIsEscaped() {
        assertEquals("'a\\\\b'", BetterRubyCodegen.toRubyStringLiteral("a\\b"));
    }

    @Test
    void interpolationSigilsStayLiteralInSingleQuotes() {
        assertEquals("'#{x} $eq'", BetterRubyCodegen.toRubyStringLiteral("#{x} $eq"));
    }

    @Test
    void controlCharacterSwitchesToADoubleQuotedLiteralThatEscapesInterpolation() {
        assertEquals("\"a\\tb\\#{x}\"", BetterRubyCodegen.toRubyStringLiteral("a\tb#{x}"));
    }

    @Test
    void otherControlCharactersUseABracedUnicodeEscape() {
        assertEquals("\"a\\u{1}b\"", BetterRubyCodegen.toRubyStringLiteral("a\u0001b"));
    }

    @Test
    void escapingForASingleQuotedLiteralRoundTrips() {
        final String raw = "it's a\\b";
        assertEquals(raw, BetterRubyCodegen.unescapeSingleQuoted(BetterRubyCodegen.escapeSingleQuoted(raw)));
    }

    @Test
    void constantNamesAreUpperCaseIdentifiers() {
        assertEquals("A_B", BetterRubyCodegen.toRubyConstantName("a-b"));
        assertEquals("O_CLOCK", BetterRubyCodegen.toRubyConstantName("o'clock"));
    }

    @Test
    void aDigitLeadingConstantNameGetsALetterPrefix() {
        assertEquals("N2FA", BetterRubyCodegen.toRubyConstantName("2fa"));
    }

    @Test
    void aValueWithNoIdentifierCharactersTakesTheEmptyName() {
        assertEquals("EMPTY", BetterRubyCodegen.toRubyConstantName("@@"));
    }

    @Test
    void aParameterNamedLikeAnInjectedArgumentGetsASuffix() {
        final BetterRubyCodegen codegen = new BetterRubyCodegen();
        assertEquals("options_param", codegen.toParamName("options"));
        assertEquals("server_param", codegen.toParamName("server"));
        assertEquals("path_param", codegen.toParamName("path"));
        assertEquals("request_content_type_param", codegen.toParamName("requestContentType"));
    }

    @Test
    void anOrdinaryParameterNameKeepsItsName() {
        assertEquals("pet_id", new BetterRubyCodegen().toParamName("petId"));
    }
}
