package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizePatternPropertiesRuleTest extends BaseRuleTest<NormalizePatternPropertiesRule> {

    private Schema<?> run(String schema) {
        final OpenAPI api = new OpenAPIV3Parser().readContents(
                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                        + "components:\n  schemas:\n    U:\n" + schema, null, null).getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return Objects.requireNonNull(api.getComponents().getSchemas().get("U"));
    }

    @Test
    @DisplayName("one pattern types the extra properties")
    void onePatternTypesTheExtras() {
        final Schema<?> schema = run("      type: object\n"
                + "      patternProperties: {'^s[0-9]+$': {type: integer, format: int32}}\n");

        assertNull(schema.getPatternProperties());
        assertEquals("int32", additional(schema).getFormat());
    }

    @Test
    @DisplayName("patterns with the same value schema type the extras, an equal additionalProperties is kept")
    void equalPatternsAndAdditionalPropertiesTypeTheExtras() {
        final Schema<?> schema = run("      type: object\n"
                + "      patternProperties: {'^a': {type: string}, '^b': {type: string}}\n"
                + "      additionalProperties: {type: string}\n");

        assertEquals("string", additional(schema).getTypes().iterator().next());
    }

    @Test
    @DisplayName("patterns with different value schemas leave the extras free-form")
    void differentPatternsLeaveTheExtrasFreeForm() {
        final Schema<?> schema = run("      type: object\n"
                + "      patternProperties: {'^a': {type: string}, '^b': {type: integer}}\n");

        assertEquals(true, schema.getAdditionalProperties());
    }

    @Test
    @DisplayName("additionalProperties false is replaced by the pattern schema")
    void falseIsReplacedByThePatternSchema() {
        final Schema<?> schema = run("      type: object\n"
                + "      patternProperties: {'^a': {type: boolean}}\n"
                + "      additionalProperties: false\n");

        assertEquals("boolean", additional(schema).getTypes().iterator().next());
    }

    @Test
    @DisplayName("a different additionalProperties schema leaves the extras free-form")
    void aDifferentAdditionalPropertiesLeavesTheExtrasFreeForm() {
        final Schema<?> schema = run("      type: object\n"
                + "      patternProperties: {'^a': {type: boolean}}\n"
                + "      additionalProperties: {type: string}\n");

        assertEquals(true, schema.getAdditionalProperties());
    }

    private static Schema<?> additional(@Nullable Schema<?> schema) {
        return (Schema<?>) Objects.requireNonNull(Objects.requireNonNull(schema).getAdditionalProperties());
    }
}
