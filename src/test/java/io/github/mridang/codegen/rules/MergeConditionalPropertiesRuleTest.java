package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MergeConditionalPropertiesRuleTest extends BaseRuleTest<MergeConditionalPropertiesRule> {

    private Schema<?> run(String schema) {
        final OpenAPI api = new OpenAPIV3Parser().readContents(
                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                        + "components:\n  schemas:\n    U:\n" + schema, null, null).getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return Objects.requireNonNull(api.getComponents().getSchemas().get("U"));
    }

    @Test
    @DisplayName("the properties of then and else become optional properties of the schema")
    void branchPropertiesBecomeOptionalProperties() {
        final Schema<?> schema = run("      type: object\n      required: [kind]\n"
                + "      properties: {kind: {type: string}}\n"
                + "      if: {properties: {kind: {const: a}}}\n"
                + "      then: {properties: {code: {type: string}}, required: [code]}\n"
                + "      else: {properties: {postcode: {type: string}}}\n");

        assertEquals(List.of("kind", "code", "postcode"), List.copyOf(schema.getProperties().keySet()));
        assertEquals(List.of("kind"), schema.getRequired());
        assertNull(schema.getIf());
        assertNull(schema.getThen());
        assertNull(schema.getElse());
    }

    @Test
    @DisplayName("a property the schema declares keeps its own schema")
    void aDeclaredPropertyKeepsItsSchema() {
        final Schema<?> schema = run("      type: object\n"
                + "      properties: {kind: {type: string}}\n"
                + "      then: {properties: {kind: {type: integer}}}\n");

        assertEquals("string",
                Objects.requireNonNull((Schema<?>) schema.getProperties().get("kind")).getTypes().iterator().next());
    }

    @Test
    @DisplayName("a property both branches declare differently keeps the then schema")
    void thenWinsOverElse() {
        final Schema<?> schema = run("      type: object\n"
                + "      then: {properties: {v: {type: integer}}}\n"
                + "      else: {properties: {v: {type: string}}}\n");

        assertEquals("integer",
                Objects.requireNonNull((Schema<?>) schema.getProperties().get("v")).getTypes().iterator().next());
    }
}
