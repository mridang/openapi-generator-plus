package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentEncodingRuleTest extends BaseRuleTest<ContentEncodingRule> {

    private OpenAPI specWithComponentSchema(Schema<?> schema) {
        return new OpenAPI()
            .components(new Components().addSchemas("Target", schema));
    }

    private Schema<?> targetSchema(OpenAPI openAPI) {
        return openAPI.getComponents().getSchemas().get("Target");
    }

    @Test
    @DisplayName("contentEncoding: base64 maps to format: byte")
    void base64MapsToByte() {
        Schema<?> schema = new StringSchema().contentEncoding("base64");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        assertEquals("byte", targetSchema(openAPI).getFormat());
        assertNull(targetSchema(openAPI).getContentEncoding());
    }

    @Test
    @DisplayName("contentEncoding: base64url maps to format: byte plus x-is-base64url flag")
    void base64UrlMapsToByteWithFlag() {
        Schema<?> schema = new StringSchema().contentEncoding("base64url");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> result = targetSchema(openAPI);
        assertEquals("byte", result.getFormat());
        assertNotNull(result.getExtensions(), "extensions should be present");
        assertEquals(Boolean.TRUE, result.getExtensions().get(ContentEncodingRule.X_IS_BASE64_URL));
        assertNull(result.getContentEncoding());
    }

    @Test
    @DisplayName("contentEncoding: base16 maps to format: byte plus x-is-base16 flag")
    void base16MapsToByteWithFlag() {
        Schema<?> schema = new StringSchema().contentEncoding("base16");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> result = targetSchema(openAPI);
        assertEquals("byte", result.getFormat());
        assertEquals(Boolean.TRUE, result.getExtensions().get(ContentEncodingRule.X_IS_BASE16));
        assertNull(result.getContentEncoding());
    }

    @Test
    @DisplayName("contentEncoding: binary maps to format: binary")
    void binaryMapsToBinary() {
        Schema<?> schema = new StringSchema().contentEncoding("binary");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        assertEquals("binary", targetSchema(openAPI).getFormat());
        assertNull(targetSchema(openAPI).getContentEncoding());
    }

    @Test
    @DisplayName("Unknown contentEncoding warns and clears the keyword")
    void unknownEncodingClearsKeyword() {
        Schema<?> schema = new StringSchema().contentEncoding("rot13");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> result = targetSchema(openAPI);
        assertNull(result.getFormat(), "no fake format should be invented");
        assertNull(result.getContentEncoding(), "keyword cleared once handled");
        assertNotNull(result.getDescription());
        assertTrue(result.getDescription().contains("Content encoding: rot13"));
    }

    @Test
    @DisplayName("contentMediaType is appended to the schema description")
    void contentMediaTypeAppendedToDescription() {
        Schema<?> schema = new StringSchema()
            .description("Existing summary.")
            .contentEncoding("base64")
            .contentMediaType("image/png");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> result = targetSchema(openAPI);
        assertEquals("byte", result.getFormat());
        assertNotNull(result.getDescription());
        assertTrue(result.getDescription().contains("Existing summary."));
        assertTrue(result.getDescription().contains("Content media type: image/png"));
        assertNull(result.getContentMediaType());
    }

    @Test
    @DisplayName("Rule descends into object properties")
    void recursesIntoProperties() {
        Schema<?> nested = new StringSchema().contentEncoding("base64");
        ObjectSchema parent = (ObjectSchema) new ObjectSchema().addProperty("payload", nested);
        OpenAPI openAPI = specWithComponentSchema(parent);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> inner = (Schema<?>) targetSchema(openAPI).getProperties().get("payload");
        assertEquals("byte", inner.getFormat());
        assertNull(inner.getContentEncoding());
    }

    @Test
    @DisplayName("Rule descends into array items")
    void recursesIntoArrayItems() {
        ArraySchema parent = new ArraySchema();
        parent.setItems(new StringSchema().contentEncoding("base64url"));
        OpenAPI openAPI = specWithComponentSchema(parent);
        rule.apply(openAPI, Map.of(), logger);
        Schema<?> items = (Schema<?>) ((ArraySchema) targetSchema(openAPI)).getItems();
        assertEquals("byte", items.getFormat());
        assertEquals(Boolean.TRUE, items.getExtensions().get(ContentEncodingRule.X_IS_BASE64_URL));
    }

    @Test
    @DisplayName("Rule handles request bodies and responses on operations")
    void recursesIntoOperationContent() {
        StringSchema reqSchema = (StringSchema) new StringSchema().contentEncoding("base64");
        StringSchema respSchema = (StringSchema) new StringSchema().contentEncoding("binary");
        OpenAPI openAPI = new OpenAPI()
            .path("/file", new PathItem().post(new Operation()
                .requestBody(new RequestBody().content(new Content()
                    .addMediaType("application/json", new MediaType().schema(reqSchema))))
                .responses(new ApiResponses().addApiResponse("200",
                    new ApiResponse().content(new Content()
                        .addMediaType("application/json", new MediaType().schema(respSchema)))))));
        rule.apply(openAPI, Map.of(), logger);
        assertEquals("byte", reqSchema.getFormat());
        assertEquals("binary", respSchema.getFormat());
    }

    @Test
    @DisplayName("Rule is idempotent: a second pass is a no-op")
    void idempotent() {
        Schema<?> schema = new StringSchema().contentEncoding("base64url");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        String firstFormat = targetSchema(openAPI).getFormat();
        Object firstFlag = targetSchema(openAPI).getExtensions().get(ContentEncodingRule.X_IS_BASE64_URL);
        rule.apply(openAPI, Map.of(), logger);
        assertEquals(firstFormat, targetSchema(openAPI).getFormat());
        assertEquals(firstFlag, targetSchema(openAPI).getExtensions().get(ContentEncodingRule.X_IS_BASE64_URL));
    }

    @Test
    @DisplayName("Existing non-matching format is not overwritten")
    void doesNotOverwriteExistingFormat() {
        Schema<?> schema = new StringSchema().format("date-time").contentEncoding("base64");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        assertEquals("date-time", targetSchema(openAPI).getFormat());
        assertNull(targetSchema(openAPI).getContentEncoding());
    }

    @Test
    @DisplayName("Schema without contentEncoding or contentMediaType is untouched")
    void noOpWhenAbsent() {
        Schema<?> schema = new StringSchema().format("uuid").description("hi");
        OpenAPI openAPI = specWithComponentSchema(schema);
        rule.apply(openAPI, Map.of(), logger);
        assertEquals("uuid", targetSchema(openAPI).getFormat());
        assertEquals("hi", targetSchema(openAPI).getDescription());
        assertFalse(targetSchema(openAPI).getExtensions() != null
            && targetSchema(openAPI).getExtensions().containsKey(ContentEncodingRule.X_IS_BASE64_URL));
    }
}
