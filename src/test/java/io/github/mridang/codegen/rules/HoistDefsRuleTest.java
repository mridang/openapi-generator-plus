package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoistDefsRuleTest extends BaseRuleTest<HoistDefsRule> {

    private OpenAPI run(String schemas) {
        final OpenAPI api = new OpenAPIV3Parser().readContents(
                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                        + "components:\n  schemas:\n" + schemas, null, null).getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return api;
    }

    @Test
    @DisplayName("a definition becomes a component and its references follow it")
    void aDefinitionBecomesAComponent() {
        final OpenAPI api = run("    Order:\n      type: object\n      $defs:\n"
                + "        Line: {type: object, properties: {sku: {type: string}}}\n"
                + "      properties:\n        line: {$ref: '#/components/schemas/Order/$defs/Line'}\n"
                + "        lines: {type: array, items: {$ref: '#/components/schemas/Order/$defs/Line'}}\n");
        @SuppressWarnings("rawtypes") final Map<String, Schema> schemas = api.getComponents().getSchemas();
        final Schema<?> order = nn(schemas.get("Order"));

        assertNotNull(nn(schemas.get("Line")).getProperties().get("sku"));
        assertEquals("#/components/schemas/Line", nn((Schema<?>) order.getProperties().get("line")).get$ref());
        assertEquals("#/components/schemas/Line", nn(nn((Schema<?>) order.getProperties().get("lines")).getItems()).get$ref());
        assertNull(order.getExtensions());
    }

    @Test
    @DisplayName("a definition named like an existing component takes its owner's name in front")
    void aTakenNameGetsTheOwnerPrefix() {
        final OpenAPI api = run("    Line: {type: string}\n    Order:\n      $defs:\n"
                + "        line: {type: integer}\n"
                + "      properties:\n        n: {$ref: '#/components/schemas/Order/$defs/line'}\n"
                + "    Other:\n      $defs:\n        Line: {type: boolean}\n");
        @SuppressWarnings("rawtypes") final Map<String, Schema> schemas = api.getComponents().getSchemas();

        assertEquals("string", nn(schemas.get("Line")).getTypes().iterator().next());
        assertNotNull(schemas.get("line"));
        assertNotNull(schemas.get("OtherLine"));
    }

    @Test
    @DisplayName("a definition nested in a definition is hoisted too")
    void nestedDefinitionsAreHoisted() {
        final OpenAPI api = run("    Order:\n      $defs:\n        Line:\n          $defs:\n"
                + "            Sku: {type: string}\n"
                + "          properties:\n            sku: {$ref: '#/components/schemas/Line/$defs/Sku'}\n");
        @SuppressWarnings("rawtypes") final Map<String, Schema> schemas = api.getComponents().getSchemas();

        assertNotNull(schemas.get("Sku"));
        assertEquals("#/components/schemas/Sku",
                nn((Schema<?>) nn(schemas.get("Line")).getProperties().get("sku")).get$ref());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
