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

    private static List<String> messages(OpenAPI spec) {
        return SpecWarnings.collect(spec).stream().map(SpecWarnings.Warning::toString)
            .collect(java.util.stream.Collectors.toList());
    }

    private static Operation operationWithPathParameter(String name) {
        return new Operation().addParametersItem(
            new io.swagger.v3.oas.models.parameters.PathParameter().name(name));
    }

    @Test
    @DisplayName("Accepts path templates the OpenAPI 3.2 grammar allows")
    void validPathTemplatesAreAccepted() {
        for (String path : List.of("/", "/items", "/items/", "/items/{id}", "/items/{id}.json",
            "/a-b_c~d/%20x/{id}:archive", "/items;v=1/@me")) {
            List<String> names = new java.util.ArrayList<>();
            assertEquals(null, SpecWarnings.templateProblem(path, names), path);
        }
    }

    @Test
    @DisplayName("Reports a path template that breaks the OpenAPI 3.2 grammar, with its reason")
    void invalidPathTemplatesAreReported() {
        Map<String, String> cases = new java.util.LinkedHashMap<>();
        cases.put("items", "it must start with '/'");
        cases.put("/items//{id}", "it has an empty segment");
        cases.put("/items/{id", "the expression at '{id' is not closed");
        cases.put("/items/{a{b}}", "the expression at '{a{b}}' is not closed");
        cases.put("/items/id}", "it has a '}' that closes no expression");
        cases.put("/items/{}", "it has an empty expression '{}'");
        cases.put("/{id}/x/{id}", "the expression '{id}' appears more than once");
        cases.put("/items/a b", "the character ' ' must be percent-encoded");
        cases.put("/items/%zz", "'%' must start a percent-encoded octet");
        cases.put("/items?q", "the character '?' must be percent-encoded");
        cases.forEach((path, reason) ->
            assertEquals(reason, SpecWarnings.templateProblem(path, new java.util.ArrayList<>()), path));
    }

    @Test
    @DisplayName("Lists an invalid path and a template expression no path parameter declares")
    void pathTemplateWarningsCarryThePointer() {
        OpenAPI spec = new OpenAPI()
            .path("/items/{id", new PathItem().get(new Operation()))
            .path("/items/{id}/{part}", new PathItem().get(operationWithPathParameter("id")));

        assertEquals(List.of(
            "#/paths/~1items~1{id: the path template '/items/{id' is not valid: the expression at"
                + " '{id' is not closed",
            "#/paths/~1items~1{id}~1{part}/get: the path template expression '{part}' has no path"
                + " parameter named 'part'"), messages(spec));
    }

    @Test
    @DisplayName("A path parameter declared on the path item counts for each of its operations")
    void pathItemParametersCount() {
        OpenAPI spec = new OpenAPI().path("/items/{id}", new PathItem()
            .addParametersItem(new io.swagger.v3.oas.models.parameters.PathParameter().name("id"))
            .get(new Operation()));

        assertTrue(SpecWarnings.collect(spec).isEmpty());
    }

    @Test
    @DisplayName("Lists a server URL that breaks the OpenAPI 3.2 grammar and an undeclared variable")
    void serverUrlWarningsCarryThePointer() {
        io.swagger.v3.oas.models.servers.ServerVariables variables =
            new io.swagger.v3.oas.models.servers.ServerVariables();
        variables.addServerVariable("region", new io.swagger.v3.oas.models.servers.ServerVariable()
            ._default("eu")._enum(List.of("us", "ap")));
        OpenAPI spec = new OpenAPI()
            .addServersItem(new io.swagger.v3.oas.models.servers.Server().url("https://{region}.example.com/{version}")
                .variables(variables))
            .addServersItem(new io.swagger.v3.oas.models.servers.Server().url("https://example.com/a b"))
            .addServersItem(new io.swagger.v3.oas.models.servers.Server().url("https://{host}/{host}"));

        assertEquals(List.of(
            "#/servers/0/url: the server variable '{version}' is not declared in variables",
            "#/servers/0/variables/region/default: the default 'eu' is not one of the enum values",
            "#/servers/1/url: the server URL 'https://example.com/a b' is not valid: the character"
                + " 'U+0020' is not allowed",
            "#/servers/2/url: the server URL 'https://{host}/{host}' is not valid: the variable"
                + " '{host}' appears more than once"), messages(spec));
    }

    @Test
    @DisplayName("Accepts server URLs the OpenAPI 3.2 grammar allows")
    void validServerUrlsAreAccepted() {
        for (String url : List.of("/", "https://api.example.com/v1", "http://localhost:8080/{base}",
            "https://example.com/caf%C3%A9", "https://例え.jp/")) {
            assertEquals(null, SpecWarnings.serverUrlProblem(url, new java.util.ArrayList<>()), url);
        }
    }
}
