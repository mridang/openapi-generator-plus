package io.github.mridang.codegen.generators.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Validates {@link BetterNodeCodegen#quoteSingle(String)}, which turns an
 * apiKey scheme name into a TypeScript string literal. A legal header name
 * may contain a single quote or a backslash, which must not break the
 * generated source.
 */
class BetterNodeCodegenTest {

    @Test
    void quotesAPlainName() {
        assertEquals("'X-Api-Key'", BetterNodeCodegen.quoteSingle("X-Api-Key"));
    }

    @Test
    void escapesQuoteAndBackslash() {
        assertEquals("'X-Api\\'Key\\\\'", BetterNodeCodegen.quoteSingle("X-Api'Key\\"));
    }

    @Test
    void leavesDollarLiteral() {
        assertEquals("'$apiKey'", BetterNodeCodegen.quoteSingle("$apiKey"));
    }
}
