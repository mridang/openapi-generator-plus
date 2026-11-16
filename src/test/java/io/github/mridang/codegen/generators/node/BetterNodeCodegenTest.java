package io.github.mridang.codegen.generators.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenParameter;

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

    @Test
    void uploadParametersAcceptABlobAsWellAsABuffer() {
        final CodegenParameter file = new CodegenParameter();
        file.isFile = true;
        file.dataType = "Array<Buffer>";
        assertEquals("Array<Buffer | Blob>", BetterNodeCodegen.uploadType(file));
        file.dataType = "Buffer";
        assertEquals("Buffer | Blob", BetterNodeCodegen.uploadType(file));
    }

    @Test
    void nonUploadParametersKeepTheirType() {
        final CodegenParameter plain = new CodegenParameter();
        plain.dataType = "string";
        assertEquals("string", BetterNodeCodegen.uploadType(plain));
    }

    @Test
    void modelsNamedLikeTheGlobalErrorOrResultGetThePrefix() {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        assertEquals("ModelError", codegen.toModelName("Error"));
        assertEquals("ModelResult", codegen.toModelName("Result"));
        assertEquals("Category", codegen.toModelName("Category"));
    }
}
