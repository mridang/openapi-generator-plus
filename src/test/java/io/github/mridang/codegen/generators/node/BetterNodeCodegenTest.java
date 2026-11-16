package io.github.mridang.codegen.generators.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenServer;

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

    @Test
    void parametersNamedLikeInjectedArgumentsGetASuffix() {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        assertEquals("optionsParam", codegen.toParamName("options"));
        assertEquals("requestOptionsParam", codegen.toParamName("requestOptions"));
        assertEquals("serverParam", codegen.toParamName("server"));
        assertEquals("petId", codegen.toParamName("petId"));
    }

    private static CodegenOperation operation(
            final boolean withServer, final boolean withQuery, final boolean optionsRequired) {
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "getThing";
        final CodegenParameter pathParam = new CodegenParameter();
        pathParam.paramName = "thingId";
        pathParam.dataType = "number";
        op.pathParams.add(pathParam);
        if (withQuery) {
            final CodegenParameter query = new CodegenParameter();
            query.paramName = "filter";
            query.dataType = "string";
            op.queryParams.add(query);
        }
        if (withServer) {
            op.servers = new ArrayList<>(List.of(new CodegenServer()));
        }
        final Map<String, Object> decorator = new HashMap<>();
        decorator.put("optionsParamRequired", optionsRequired);
        op.vendorExtensions.put("op", decorator);
        return op;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> wrapperFields(final CodegenOperation op) {
        final Map<String, Object> decorator =
                java.util.Objects.requireNonNull((Map<String, Object>) op.vendorExtensions.get("op"));
        return java.util.Objects.requireNonNull(
                (List<Map<String, Object>>) decorator.get("requestWrapperFields"));
    }

    private static List<String> names(final List<Map<String, Object>> fields) {
        final List<String> names = new ArrayList<>();
        for (final Map<String, Object> field : fields) {
            names.add((String) field.get("name"));
        }
        return names;
    }

    @Test
    void theServerComesAfterTheRequiredParametersAndBeforeTheOptionsObject() {
        final CodegenOperation op = operation(true, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(
                List.of("thingId", "server", "options", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void aServerOnlySignatureEndsWithTheRequestOptions() {
        final CodegenOperation op = operation(true, false, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(List.of("thingId", "server", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void aServerBeforeARequiredOptionsObjectIsSpelledOutNotOptional() {
        final CodegenOperation op = operation(true, true, true);
        new BetterNodeCodegen().populateRequestWrapper(op);
        final Map<String, Object> server = wrapperFields(op).get(1);
        assertEquals("GetThingServer | undefined", server.get("type"));
        assertEquals(false, server.get("optional"));
    }

    @Test
    void aServerBeforeAnOptionalOptionsObjectIsOptional() {
        final CodegenOperation op = operation(true, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        final Map<String, Object> server = wrapperFields(op).get(1);
        assertEquals("GetThingServer", server.get("type"));
        assertEquals(true, server.get("optional"));
    }

    @Test
    void anOperationWithoutAServerHasNoServerArgument() {
        final CodegenOperation op = operation(false, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(List.of("thingId", "options", "requestOptions"), names(wrapperFields(op)));
    }
}
