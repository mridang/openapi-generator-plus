package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizeConstRuleTest extends BaseRuleTest<NormalizeConstRule> {

    private OpenAPI run(String schemas) {
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                                        + "components:\n  schemas:\n"
                                        + schemas,
                                null,
                                null)
                        .getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return api;
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("a const with no type takes the type of its value")
    void aTypelessConstTakesTheTypeOfItsValue() {
        final OpenAPI api = run("    A: {const: 3}\n    B: {const: abc}\n    C: {const: true}\n");
        final Map<String, Schema> schemas = api.getComponents().getSchemas();

        assertEquals("integer", nn(schemas.get("A")).getType());
        assertEquals("string", nn(schemas.get("B")).getType());
        assertEquals("boolean", nn(schemas.get("C")).getType());
    }

    @Test
    @DisplayName("a oneOf of constants becomes one enum")
    void aOneOfOfConstantsBecomesAnEnum() {
        final OpenAPI api =
                run("    Colour:\n      oneOf:\n        - {const: red, title: Red}\n"
                        + "        - {const: green}\n");
        final Schema<?> colour = nn(api.getComponents().getSchemas().get("Colour"));

        assertNull(colour.getOneOf());
        assertEquals("string", colour.getType());
        assertEquals(List.of("red", "green"), colour.getEnum());
    }

    @Test
    @DisplayName("a oneOf with a member that is not a constant is untouched")
    void aMixedOneOfIsUntouched() {
        final OpenAPI api =
                run("    Mixed:\n      oneOf:\n        - {const: red}\n        - {type: string}\n");

        assertEquals(2, nn(api.getComponents().getSchemas().get("Mixed")).getOneOf().size());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
