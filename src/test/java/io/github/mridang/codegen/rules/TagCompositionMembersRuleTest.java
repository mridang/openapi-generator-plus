package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TagCompositionMembersRule Tests")
class TagCompositionMembersRuleTest {

    private static final Logger logger = LoggerFactory.getLogger(TagCompositionMembersRuleTest.class);
    private TagCompositionMembersRule rule;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        rule = new TagCompositionMembersRule();
        openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
    }


    @Test
    @DisplayName("Should tag properties from a oneOf list in a mixed schema (property only defined in oneOf)")
    void shouldTagOneOfMembersInMixedSchema() {
        // 1. Create the mixed schema
        Schema<?> mixedSchema = new Schema<>()
            .title("MixedSchema")
            .type("object");

        // 2. Add a regular property to the main `properties` block
        mixedSchema.addProperties("regularProp", new Schema<>().type("string"));
        // IMPORTANT: DO NOT add "choiceProp" here. It will be flattened from oneOf.

        // 3. Define the `oneOf` block that contains the property to be tagged
        Schema<?> oneOfOption = new Schema<>()
            .type("object")
            .addProperties("choiceProp", new Schema<>().type("boolean")); // This one should be tagged
        mixedSchema.setOneOf(List.of(oneOfOption));

        // 4. Add the schema to the components
        openAPI.getComponents().addSchemas("MixedSchema", mixedSchema);

        // 5. Run the rule
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // 6. Assert the results
        Schema<?> resultSchema = openAPI.getComponents().getSchemas().get("MixedSchema");
        assertNotNull(resultSchema, "The result schema should not be null.");

        Schema<?> taggedProperty = (Schema<?>) resultSchema.getProperties().get("choiceProp");
        assertNotNull(taggedProperty, "The tagged property should exist in the schema after flattening.");

        Schema<?> regularProperty = (Schema<?>) resultSchema.getProperties().get("regularProp");
        assertNotNull(regularProperty, "The regular property should exist in the schema.");

        Map<String, Object> extensions = taggedProperty.getExtensions();
        assertNotNull(extensions, "The oneOf property should have extensions.");
        assertTrue(
            Boolean.TRUE.equals(extensions.get("x-oneof-member")),
            "The 'x-oneof-member' tag should be true."
        );

        assertNull(regularProperty.getExtensions(), "The regular property should not be tagged.");
        assertNull(resultSchema.getOneOf(), "The oneOf block should be cleared."); // Also assert oneOf is cleared
    }

}
