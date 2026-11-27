package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizeTypeArrayRuleTest extends BaseRuleTest<NormalizeTypeArrayRule> {

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
    @DisplayName("two scalar types become an anyOf of two")
    void twoScalarTypesBecomeAnAnyOf() {
        final Schema<?> schema = nn(run("    U: {type: [string, integer]}\n")
                .getComponents().getSchemas().get("U"));

        assertNull(schema.getTypes());
        assertEquals(2, schema.getAnyOf().size());
        assertEquals("string", ((Schema<?>) schema.getAnyOf().get(0)).getType());
        assertEquals("integer", ((Schema<?>) schema.getAnyOf().get(1)).getType());
    }

    @Test
    @DisplayName("an array and an object keep items and properties on their own member")
    void arrayAndObjectKeepTheirKeywords() {
        final Schema<?> schema =
                nn(run("    U:\n      type: [array, object]\n      items: {type: string}\n"
                                + "      properties: {k: {type: string}}\n")
                        .getComponents().getSchemas().get("U"));

        final Schema<?> array = (Schema<?>) schema.getAnyOf().get(0);
        final Schema<?> object = (Schema<?>) schema.getAnyOf().get(1);
        assertNotNull(array.getItems());
        assertNull(array.getProperties());
        assertNotNull(object.getProperties().get("k"));
        assertNull(schema.getProperties());
    }

    @Test
    @DisplayName("a type with null stays one type and becomes nullable")
    void aTypeWithNullStaysOneTypeAndIsNullable() {
        final Schema<?> schema = nn(run("    U: {type: [string, 'null']}\n")
                .getComponents().getSchemas().get("U"));

        assertNull(schema.getAnyOf());
        assertTrue(schema.getNullable());
        assertEquals(Set.of("string"), Set.copyOf(schema.getTypes() == null
                ? Set.of(schema.getType()) : schema.getTypes()));
    }

    @Test
    @DisplayName("the stock normaliser's int32 anyOf is rebuilt so an unformatted integer keeps no format")
    void theStockAnyOfIsRebuilt() {
        final OpenAPI api = new OpenAPIV3Parser().readContents(
                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                        + "components:\n  schemas:\n    U: {type: [string, integer]}\n",
                null, null).getOpenAPI();
        final Schema<?> schema = nn(api.getComponents().getSchemas().get("U"));
        schema.addAnyOfItem(new io.swagger.v3.oas.models.media.StringSchema());
        schema.addAnyOfItem(new io.swagger.v3.oas.models.media.IntegerSchema());
        rule.apply(api, Map.of(), logger);

        assertNull(schema.getTypes());
        assertEquals(2, schema.getAnyOf().size());
        assertEquals("integer", ((Schema<?>) schema.getAnyOf().get(1)).getType());
        assertNull(((Schema<?>) schema.getAnyOf().get(1)).getFormat());
    }

    @Test
    @DisplayName("an anyOf written next to a type array is left alone")
    void aWrittenAnyOfIsLeftAlone() {
        final Schema<?> schema = nn(run("    U:\n      type: [string, integer]\n"
                        + "      anyOf: [{type: string, minLength: 1}, {$ref: '#/components/schemas/V'}]\n"
                        + "    V: {type: integer}\n")
                .getComponents().getSchemas().get("U"));

        assertEquals(2, schema.getAnyOf().size());
        assertEquals("#/components/schemas/V", ((Schema<?>) schema.getAnyOf().get(1)).get$ref());
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
