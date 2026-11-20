package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizeIntegerWidthRuleTest extends BaseRuleTest<NormalizeIntegerWidthRule> {

    @Test
    @DisplayName("an integer with no format becomes int64 and a formatted integer is left alone")
    void unformattedIntegerBecomesInt64() {
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.0.3\ninfo: {title: t, version: '1'}\npaths: {}\n"
                                        + "components:\n  schemas:\n"
                                        + "    A: {type: integer}\n"
                                        + "    B: {type: integer, format: int32}\n"
                                        + "    C: {type: array, items: {type: integer}}\n"
                                        + "    D: {type: number}\n",
                                null,
                                null)
                        .getOpenAPI();
        rule.apply(api, Map.of(), logger);

        assertEquals("int64", nn(api.getComponents().getSchemas().get("A")).getFormat());
        assertEquals("int32", nn(api.getComponents().getSchemas().get("B")).getFormat());
        assertEquals(
                "int64", nn(nn(api.getComponents().getSchemas().get("C")).getItems()).getFormat());
        assertEquals(null, nn(api.getComponents().getSchemas().get("D")).getFormat());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
