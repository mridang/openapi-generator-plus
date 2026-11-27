package io.github.mridang.codegen;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpecWarnings Tests")
class SpecWarningsTest {

    private static final String POINTER = "#/paths/~1pets/get/responses/200/content/application~1json/schema";

    private static OpenAPI specWithResponseSchema(String ref) {
        Schema<?> schema = new Schema<>().$ref(ref);
        ApiResponse response = new ApiResponse()
            .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
        return new OpenAPI()
            .components(new Components().addSchemas("Pet", new Schema<>().type("object")))
            .path("/pets", new PathItem().get(new Operation()
                .responses(new ApiResponses().addApiResponse("200", response))));
    }

    @Test
    @DisplayName("Lists a dangling $ref with the JSON pointer of the schema that holds it")
    void danglingRefIsListedWithPointer() {
        List<SpecWarnings.Warning> warnings =
            SpecWarnings.collect(specWithResponseSchema("#/components/schemas/Missing"));

        assertEquals(1, warnings.size());
        assertEquals(POINTER, warnings.get(0).getPointer());
        assertTrue(warnings.get(0).getMessage().contains("dangling $ref '#/components/schemas/Missing'"));
    }

    @Test
    @DisplayName("Does not list a $ref that resolves inside the document")
    void resolvedRefIsNotListed() {
        assertTrue(SpecWarnings.collect(specWithResponseSchema("#/components/schemas/Pet")).isEmpty());
    }

    @Test
    @DisplayName("Lists a reference that is not a local JSON pointer as unresolved")
    void externalRefIsListed() {
        List<SpecWarnings.Warning> warnings =
            SpecWarnings.collect(specWithResponseSchema("https://example.com/pet.yaml#/Pet"));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getMessage().contains("is not a local JSON pointer"));
    }

    @Test
    @DisplayName("Decodes JSON pointer escapes when it resolves a reference")
    void escapedPointerResolves() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components().addSchemas("a/b", new Schema<>().type("object")))
            .path("/x", new PathItem().get(new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().content(new Content().addMediaType(
                    "application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/a~1b"))))))));

        assertTrue(SpecWarnings.collect(openAPI).isEmpty());
    }

    @Test
    @DisplayName("Lists callbacks, links and xml where they are keywords")
    void unsupportedKeywordsAreListed() {
        Callback callback = new Callback().addPathItem("{$request.body#/url}", new PathItem());
        Schema<?> pet = new Schema<>().type("object").xml(new XML().name("pet"));
        OpenAPI openAPI = new OpenAPI()
            .components(new Components().addSchemas("Pet", pet))
            .path("/pets", new PathItem().get(new Operation()
                .callbacks(Map.of("onEvent", callback))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                    .link("next", new Link())))));

        List<SpecWarnings.Warning> warnings = SpecWarnings.collect(openAPI);

        assertTrue(warnings.stream().anyMatch(w -> w.getPointer().equals("#/paths/~1pets/get/callbacks")));
        assertTrue(warnings.stream().anyMatch(w -> w.getPointer().equals(
            "#/paths/~1pets/get/responses/200/links")));
        assertTrue(warnings.stream().anyMatch(w -> w.getPointer().equals("#/components/schemas/Pet/xml")));
    }

    @Test
    @DisplayName("Does not treat a property named after a keyword as the keyword")
    void propertyNamedLikeKeywordIsNotListed() {
        Schema<?> pet = new Schema<>().type("object")
            .addProperty("links", new Schema<>().type("string"))
            .addProperty("xml", new Schema<>().type("string"));
        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("Pet", pet));

        assertTrue(SpecWarnings.collect(openAPI).isEmpty());
    }

    @Test
    @DisplayName("Warns by default and fails the run when strictSpec is set")
    void strictFlagFailsOnWarning() {
        OpenAPI openAPI = specWithResponseSchema("#/components/schemas/Missing");
        org.slf4j.Logger logger = LoggerFactory.getLogger(SpecWarningsTest.class);

        SpecWarnings.check(openAPI, false, logger);

        assertThrows(IllegalStateException.class, () -> SpecWarnings.check(openAPI, true, logger));
    }

    @Test
    @DisplayName("Strict mode passes a clean specification")
    void strictFlagPassesCleanSpec() {
        OpenAPI openAPI = specWithResponseSchema("#/components/schemas/Pet");
        org.slf4j.Logger logger = LoggerFactory.getLogger(SpecWarningsTest.class);

        SpecWarnings.check(openAPI, true, logger);
    }

    @Test
    @DisplayName("Lists a jsonSchemaDialect other than the OpenAPI dialect")
    void foreignDocumentDialectIsListed() {
        OpenAPI spec = specWithResponseSchema("#/components/schemas/Pet")
            .jsonSchemaDialect("http://json-schema.org/draft-07/schema#");

        List<SpecWarnings.Warning> warnings = SpecWarnings.collect(spec);

        assertEquals(1, warnings.size());
        assertEquals("#/jsonSchemaDialect", warnings.get(0).getPointer());
        assertTrue(warnings.get(0).getMessage().contains(
            "the JSON Schema dialect 'http://json-schema.org/draft-07/schema#' is not supported"));
    }

    @Test
    @DisplayName("Lists a schema whose $schema selects a dialect other than the OpenAPI dialect")
    void foreignSchemaDialectIsListed() {
        Schema<?> schema = new Schema<>().type("object");
        schema.set$schema("https://json-schema.org/draft/2019-09/schema");
        OpenAPI spec = new OpenAPI().components(new Components().addSchemas("Widget", schema));

        List<SpecWarnings.Warning> warnings = SpecWarnings.collect(spec);

        assertEquals(1, warnings.size());
        assertEquals("#/components/schemas/Widget/$schema", warnings.get(0).getPointer());
    }

    @Test
    @DisplayName("Accepts the OpenAPI 3.1 and 3.2 dialects and JSON Schema 2020-12")
    void openApiDialectsAreAccepted() {
        Schema<?> schema = new Schema<>().type("object");
        schema.set$schema("https://json-schema.org/draft/2020-12/schema");
        OpenAPI spec = new OpenAPI()
            .jsonSchemaDialect("https://spec.openapis.org/oas/3.1/dialect/base")
            .components(new Components().addSchemas("Widget", schema));

        assertTrue(SpecWarnings.collect(spec).isEmpty());
        assertTrue(SpecWarnings.isSupportedDialect("https://spec.openapis.org/oas/3.2/dialect/2025-09-17"));
    }
}
