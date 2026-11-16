package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MultipleSuccessResponsesRuleTest extends BaseRuleTest<MultipleSuccessResponsesRule> {

    private static ApiResponse json(Schema<?> schema) {
        return new ApiResponse()
                .description("ok")
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private static OpenAPI spec(ApiResponses responses) {
        final Operation op = new Operation().operationId("reserveStock").responses(responses);
        final Components components = new Components();
        components.addSchemas("Reservation", new ObjectSchema());
        components.addSchemas("Receipt", new ObjectSchema());
        return new OpenAPI().path("/r", new PathItem().post(op)).components(components);
    }

    private static Operation op(OpenAPI api) {
        return Objects.requireNonNull(Objects.requireNonNull(api.getPaths().get("/r")).getPost());
    }

    private static Schema<?> schema(OpenAPI api, String name) {
        return Objects.requireNonNull(Objects.requireNonNull(api.getComponents().getSchemas()).get(name));
    }

    private static String responseRef(OpenAPI api, String code) {
        final ApiResponse response = Objects.requireNonNull(op(api).getResponses().get(code));
        final MediaType mediaType = Objects.requireNonNull(response.getContent()).get("application/json");
        return Objects.requireNonNull(Objects.requireNonNull(mediaType).getSchema().get$ref());
    }

    private static List<String> unionRefs(Schema<?> union) {
        final List<String> refs = new java.util.ArrayList<>();
        for (final Object variant : union.getOneOf()) {
            refs.add(((Schema<?>) variant).get$ref());
        }
        return refs;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> mapping(OpenAPI api) {
        return (List<Map<String, String>>) Objects.requireNonNull(
                Objects.requireNonNull(op(api).getExtensions()).get(MultipleSuccessResponsesRule.X_SUCCESS_RESPONSES));
    }

    @Test
    @DisplayName("Should leave a single 2xx schema untouched")
    void singleSuccessSchemaIsUnchanged() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("400", new ApiResponse().description("bad")));

        rule.apply(api, Map.of(), logger);

        assertNull(op(api).getExtensions());
        assertEquals("#/components/schemas/Reservation", responseRef(api, "200"));
        assertFalse(api.getComponents().getSchemas().containsKey("ReserveStockResponse"));
    }

    @Test
    @DisplayName("Should leave a no-content 2xx next to one schema untouched")
    void noContentNextToOneSchemaIsUnchanged() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("204", new ApiResponse().description("none")));

        rule.apply(api, Map.of(), logger);

        assertNull(op(api).getExtensions());
    }

    @Test
    @DisplayName("Should collapse identical schemas under two 2xx codes")
    void identicalSchemasCollapse() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("201", json(ref("Reservation"))));

        rule.apply(api, Map.of(), logger);

        assertNull(op(api).getExtensions());
        assertFalse(api.getComponents().getSchemas().containsKey("ReserveStockResponse"));
    }

    @Test
    @DisplayName("Should synthesise a oneOf union and a status mapping for two schemas")
    void twoSchemasGetUnionAndMapping() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("201", json(ref("Receipt")))
                .addApiResponse("400", new ApiResponse().description("bad")));

        rule.apply(api, Map.of(), logger);

        final Schema<?> union = schema(api, "ReserveStockResponse");
        assertEquals(List.of("#/components/schemas/Reservation", "#/components/schemas/Receipt"),
                unionRefs(union));
        assertEquals(List.of(Map.of("status", "200", "ref", "#/components/schemas/Reservation"),
                        Map.of("status", "201", "ref", "#/components/schemas/Receipt")),
                mapping(api));
        assertEquals("#/components/schemas/ReserveStockResponse",
                responseRef(api, "201"));
        assertEquals("#/components/schemas/ReserveStockResponse",
                responseRef(api, "200"));
    }

    @Test
    @DisplayName("Should keep every status code, with a shared schema as one variant")
    void sharedSchemaIsOneVariantButKeepsEveryStatus() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("202", json(ref("Reservation")))
                .addApiResponse("201", json(ref("Receipt"))));

        rule.apply(api, Map.of(), logger);

        assertEquals(List.of("200", "201", "202"), mapping(api).stream().map(m -> m.get("status")).collect(java.util.stream.Collectors.toList()));
        assertEquals(2, unionRefs(schema(api, "ReserveStockResponse")).size());
    }

    @Test
    @DisplayName("Should order by status code, not by declaration order, with a 2XX range last")
    void orderingIsDeterministic() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("2XX", json(ref("Receipt")))
                .addApiResponse("201", json(ref("Receipt")))
                .addApiResponse("200", json(ref("Reservation"))));

        rule.apply(api, Map.of(), logger);

        assertEquals(List.of("200", "201", "2XX"), mapping(api).stream().map(m -> m.get("status")).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of("#/components/schemas/Reservation", "#/components/schemas/Receipt"),
                unionRefs(schema(api, "ReserveStockResponse")));
    }

    @Test
    @DisplayName("Should hoist inline schemas into components and reuse equal ones")
    void inlineSchemasAreHoisted() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(new ObjectSchema().addProperty("a", new StringSchema())))
                .addApiResponse("201", json(new ObjectSchema().addProperty("b", new StringSchema())))
                .addApiResponse("202", json(new ObjectSchema().addProperty("a", new StringSchema()))));

        rule.apply(api, Map.of(), logger);

        assertTrue(api.getComponents().getSchemas().containsKey("ReserveStockResponse1"));
        assertTrue(api.getComponents().getSchemas().containsKey("ReserveStockResponse2"));
        assertEquals(List.of("#/components/schemas/ReserveStockResponse1",
                        "#/components/schemas/ReserveStockResponse2",
                        "#/components/schemas/ReserveStockResponse1"),
                mapping(api).stream().map(m -> m.get("ref")).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    @DisplayName("Should not overwrite an existing component named like the union")
    void unionNameAvoidsCollisions() {
        final OpenAPI api = spec(new ApiResponses()
                .addApiResponse("200", json(ref("Reservation")))
                .addApiResponse("201", json(ref("Receipt"))));
        api.getComponents().addSchemas("ReserveStockResponse", new ObjectSchema());

        rule.apply(api, Map.of(), logger);

        assertEquals("ReserveStockResponse2", Objects.requireNonNull(op(api).getExtensions()).get(MultipleSuccessResponsesRule.X_SUCCESS_UNION));
    }
}
