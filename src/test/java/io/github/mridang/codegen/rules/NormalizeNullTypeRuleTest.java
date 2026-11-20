package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizeNullTypeRuleTest extends BaseRuleTest<NormalizeNullTypeRule> {

    @Test
    @DisplayName("a schema of type null alone becomes an untyped nullable schema")
    void typeNullBecomesUntypedNullable() {
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                                        + "components:\n  schemas:\n    N: {type: 'null'}\n"
                                        + "    S: {type: string}\n",
                                null,
                                null)
                        .getOpenAPI();
        rule.apply(api, Map.of(), logger);

        final Schema<?> nullOnly = nn(api.getComponents().getSchemas().get("N"));
        assertNull(nullOnly.getType());
        assertNull(nullOnly.getTypes());
        assertTrue(nullOnly.getNullable());
        assertEquals(java.util.Set.of("string"), nn(api.getComponents().getSchemas().get("S")).getTypes());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
