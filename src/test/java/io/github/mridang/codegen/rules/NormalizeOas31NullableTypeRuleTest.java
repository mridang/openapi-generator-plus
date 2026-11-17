package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizeOas31NullableTypeRuleTest extends BaseRuleTest<NormalizeOas31NullableTypeRule> {

    private static Set<String> typesOf(String... names) {
        final Set<String> types = new LinkedHashSet<>();
        for (final String n : names) {
            types.add(n);
        }
        return types;
    }

    private static Schema<?> schemaOf(String... types) {
        final Schema<?> schema = new Schema<>();
        schema.setTypes(typesOf(types));
        return schema;
    }

    private static Schema<?> component(OpenAPI openAPI, String name) {
        return Objects.requireNonNull(
                Objects.requireNonNull(openAPI.getComponents()).getSchemas().get(name));
    }

    private OpenAPI withSchema(String name, Schema<?> schema) {
        final OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas(name, schema));
        rule.apply(openAPI, Map.of(), logger);
        return openAPI;
    }

    @Test
    @DisplayName("[string, null] becomes a nullable string")
    void aNullUnionOfOneTypeBecomesANullableType() {
        final Schema<?> schema = component(withSchema("A", schemaOf("string", "null")), "A");

        assertTrue(schema.getNullable());
        assertEquals("string", schema.getType());
        assertNull(schema.getTypes());
    }

    @Test
    @DisplayName("a type array without null is left alone")
    void aUnionWithoutNullIsUntouched() {
        final Schema<?> schema = component(withSchema("A", schemaOf("string", "integer")), "A");

        assertNull(schema.getNullable());
        assertEquals(typesOf("string", "integer"), schema.getTypes());
    }

    @Test
    @DisplayName("[string, integer, null] keeps both types and becomes nullable")
    void aNullUnionOfSeveralTypesKeepsTheTypes() {
        final Schema<?> schema =
                component(withSchema("A", schemaOf("string", "integer", "null")), "A");

        assertTrue(schema.getNullable());
        assertEquals(typesOf("string", "integer"), schema.getTypes());
    }

    @Test
    @DisplayName("[null] alone clears the types and becomes nullable")
    void aLoneNullTypeIsCleared() {
        final Schema<?> schema = component(withSchema("A", schemaOf("null")), "A");

        assertTrue(schema.getNullable());
        assertNull(schema.getTypes());
    }

    @Test
    @DisplayName("a null union inside properties, items, additionalProperties and oneOf is rewritten")
    void nestedSchemasAreRewritten() {
        final Schema<?> property = schemaOf("string", "null");
        final Schema<?> items = schemaOf("integer", "null");
        final Schema<?> extra = schemaOf("boolean", "null");
        final Schema<?> variant = schemaOf("number", "null");
        final Schema<?> holder = new Schema<>();
        holder.addProperty("name", property);
        final Schema<?> list = new Schema<>();
        list.setItems(items);
        final Schema<?> map = new Schema<>();
        map.setAdditionalProperties(extra);
        final Schema<?> union = new Schema<>();
        union.addOneOfItem(variant);
        final OpenAPI openAPI =
                new OpenAPI()
                        .components(
                                new Components()
                                        .addSchemas("Holder", holder)
                                        .addSchemas("List", list)
                                        .addSchemas("Map", map)
                                        .addSchemas("Union", union));

        rule.apply(openAPI, Map.of(), logger);

        assertEquals("string", property.getType());
        assertEquals("integer", items.getType());
        assertEquals("boolean", extra.getType());
        assertEquals("number", variant.getType());
        assertTrue(variant.getNullable());
    }

    @Test
    @DisplayName("a nullable union in a response body is rewritten")
    void operationSchemasAreRewritten() {
        final Schema<?> body = schemaOf("string", "null");
        final ApiResponse response =
                new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(body)));
        final OpenAPI openAPI =
                new OpenAPI()
                        .path(
                                "/x",
                                new PathItem()
                                        .get(
                                                new Operation()
                                                        .responses(new ApiResponses().addApiResponse("200", response))));

        rule.apply(openAPI, Map.of(), logger);

        assertEquals("string", body.getType());
        assertTrue(body.getNullable());
    }

    @Test
    @DisplayName("a self-referential schema terminates")
    void selfReferenceDoesNotRecurseForever() {
        final Schema<?> node = new Schema<>();
        node.setTypes(typesOf("object", "null"));
        node.addProperty("next", node);

        rule.apply(new OpenAPI().components(new Components().addSchemas("Node", node)), Map.of(), logger);

        assertEquals("object", node.getType());
    }

    @Test
    @DisplayName("a document without components or paths is accepted")
    void anEmptyDocumentIsAccepted() {
        rule.apply(new OpenAPI(), Map.of(), logger);
    }
}
