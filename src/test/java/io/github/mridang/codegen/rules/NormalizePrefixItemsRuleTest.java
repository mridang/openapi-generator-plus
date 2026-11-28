package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizePrefixItemsRuleTest extends BaseRuleTest<NormalizePrefixItemsRule> {

    @SuppressWarnings("rawtypes")
    private static Schema<?> tuple(Schema... items) {
        final Schema<?> schema = new Schema<>();
        schema.setType("array");
        schema.setPrefixItems(List.of(items));
        return schema;
    }

    private Schema<?> run(Schema<?> schema) {
        rule.apply(
                new OpenAPI().components(new Components().addSchemas("Tuple", schema)),
                Map.of(),
                logger);
        return schema;
    }

    @Test
    @DisplayName("prefixItems becomes an array of any with the layout in the description")
    void aTupleBecomesAnArrayOfAny() {
        final Schema<?> schema = run(tuple(new StringSchema(), new IntegerSchema().format("int64")));

        assertNull(schema.getPrefixItems());
        assertEquals("array", schema.getType());
        assertNotNull(schema.getItems());
        assertEquals(
                "Tuple of 2 positional items: [string, integer(int64)]", schema.getDescription());
    }

    @Test
    @DisplayName("a $ref item is named by its component and an untyped item is any")
    void refAndUntypedItemsAreDescribed() {
        final Schema<?> reference = new Schema<>();
        reference.set$ref("#/components/schemas/Pet");

        final Schema<?> schema = run(tuple(reference, new Schema<>()));

        assertEquals("Tuple of 2 positional items: [Pet, any]", schema.getDescription());
    }

    @Test
    @DisplayName("an existing description is kept and the layout is appended once")
    void theDescriptionIsAppendedOnce() {
        final Schema<?> schema = tuple(new StringSchema());
        schema.setDescription("A pair.");
        run(schema);
        run(schema);

        assertEquals("A pair.\nTuple of 1 positional items: [string]", schema.getDescription());
    }

    @Test
    @DisplayName("an empty or absent prefixItems is not a tuple")
    void anEmptyPrefixItemsIsLeftAlone() {
        final Schema<?> schema = new Schema<>();
        schema.setType("array");
        schema.setPrefixItems(List.of());
        run(schema);

        assertNull(schema.getDescription());
        assertNull(schema.getItems());
    }

    @Test
    @DisplayName("a tuple nested in a property is rewritten")
    void aNestedTupleIsRewritten() {
        final Schema<?> inner = tuple(new StringSchema());
        final Schema<?> holder = new Schema<>();
        holder.addProperty("pair", inner);

        run(holder);

        assertNull(inner.getPrefixItems());
        assertTrue(Objects.requireNonNull(inner.getDescription()).startsWith("Tuple of 1"));
    }

    @Test
    @DisplayName("a self-referential schema terminates")
    void selfReferenceDoesNotRecurseForever() {
        final Schema<?> node = new Schema<>();
        node.addProperty("next", node);

        run(node);
    }

    @Test
    @DisplayName("a document without components or paths is accepted")
    void anEmptyDocumentIsAccepted() {
        rule.apply(new OpenAPI(), Map.of(), logger);
    }
}
