package io.github.mridang.codegen.generators.php;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Validates the string-literal rendering that the PHP authenticator wrappers use
 * for an apiKey parameter name, token URL and scope names, so a legal header or
 * query name never breaks the generated source.
 */
class BetterPHPCodegenTest {

    @Test
    void quoteInNameIsEscaped() {
        assertEquals("'X-Api\\'Key'", BetterPHPCodegen.toPhpStringLiteral("X-Api'Key"));
    }

    @Test
    void backslashInNameIsEscaped() {
        assertEquals("'a\\\\b'", BetterPHPCodegen.toPhpStringLiteral("a\\b"));
    }

    @Test
    void dollarSignStaysLiteralInSingleQuotes() {
        assertEquals("'$apiKey'", BetterPHPCodegen.toPhpStringLiteral("$apiKey"));
    }

    @Test
    void controlCharacterUsesDoubleQuotedEscape() {
        assertEquals("\"a\\tb\\$c\"", BetterPHPCodegen.toPhpStringLiteral("a\tb$c"));
    }
}
