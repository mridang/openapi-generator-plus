package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InlinePathItemReferencesRuleTest extends BaseRuleTest<InlinePathItemReferencesRule> {

    private OpenAPI run() {
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths:\n"
                                        + "  /a: {$ref: '#/components/pathItems/Shared'}\n"
                                        + "  /b: {$ref: '#/components/pathItems/Shared'}\n"
                                        + "components:\n  pathItems:\n    Shared:\n"
                                        + "      summary: Shared summary\n      get:\n"
                                        + "        operationId: getShared\n"
                                        + "        responses: {'200': {description: ok}}\n",
                                null,
                                null)
                        .getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return api;
    }

    @Test
    @DisplayName("a path item reference is replaced by the referenced operations")
    void thePathGetsTheOperations() {
        final OpenAPI api = run();

        assertNotNull(nn(api.getPaths().get("/a")).getGet());
        assertNull(nn(api.getPaths().get("/a")).get$ref());
        assertEquals("getShared", nn(api.getPaths().get("/a")).getGet().getOperationId());
    }

    @Test
    @DisplayName("an operationId used by a second path is dropped from the copy")
    void aRepeatedOperationIdIsDroppedFromTheCopy() {
        final OpenAPI api = run();

        assertNotNull(nn(api.getPaths().get("/b")).getGet());
        assertNull(nn(api.getPaths().get("/b")).getGet().getOperationId());
    }

    @Test
    @DisplayName("the path item summary reaches an operation with none")
    void theSummaryReachesTheOperation() {
        assertEquals("Shared summary", nn(nn(run().getPaths().get("/a")).getGet()).getSummary());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
