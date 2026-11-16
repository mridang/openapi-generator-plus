package io.github.mridang.codegen.generators.python;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Validates the string-literal rendering the Python authenticator wrapper uses
 * for an apiKey scheme name, so a legal header or query name never breaks the
 * generated source.
 */
class BetterPythonCodegenTest {

    @Test
    void quoteAndBackslashInAnApiKeyNameAreEscaped() {
        assertEquals("X-Api\\\"Key\\\\", BetterPythonCodegen.pythonStringBody("X-Api\"Key\\"));
    }

    @Test
    void singleQuoteDollarAndInterpolationOpenersStayLiteral() {
        assertEquals("X-Api'Key$x#{y}", BetterPythonCodegen.pythonStringBody("X-Api'Key$x#{y}"));
    }

    @Test
    void controlCharactersUseEscapeSequences() {
        assertEquals("a\\nb\\rc\\td", BetterPythonCodegen.pythonStringBody("a\nb\rc\td"));
        assertEquals("a\\x01b\\x1f", BetterPythonCodegen.pythonStringBody("a" + (char) 1 + "b" + (char) 31));
    }

    @Test
    void anOrdinaryNameIsUnchanged() {
        assertEquals("X-API-Key", BetterPythonCodegen.pythonStringBody("X-API-Key"));
    }
}
