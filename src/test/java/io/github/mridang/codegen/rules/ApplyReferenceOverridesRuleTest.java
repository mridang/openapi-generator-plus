package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplyReferenceOverridesRuleTest extends BaseRuleTest<ApplyReferenceOverridesRule> {

    private static final String SPEC = "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths:\n"
            + "  /items/{id}:\n"
            + "    parameters:\n"
            + "      - $ref: '#/components/parameters/Id'\n"
            + "        description: The item to read\n"
            + "    get:\n"
            + "      operationId: getItem\n"
            + "      parameters:\n"
            + "        - $ref: '#/components/parameters/Limit'\n"
            + "        - $ref: '#/components/parameters/Alias'\n"
            + "          description: How many to read here\n"
            + "      responses:\n"
            + "        '200':\n"
            + "          $ref: '#/components/responses/Found'\n"
            + "          description: The item that was read\n"
            + "        '404':\n"
            + "          description: Missing\n"
            + "          content:\n"
            + "            application/json:\n"
            + "              examples:\n"
            + "                gone:\n"
            + "                  $ref: '#/components/examples/Gone'\n"
            + "                  summary: An item that is gone\n"
            + "components:\n"
            + "  parameters:\n"
            + "    Id: {name: id, in: path, required: true, description: An id, schema: {type: string}}\n"
            + "    Limit: {name: limit, in: query, description: A limit, schema: {type: integer}}\n"
            + "    Alias: {$ref: '#/components/parameters/Limit'}\n"
            + "  responses:\n"
            + "    Found: {description: Found it}\n"
            + "  examples:\n"
            + "    Gone: {summary: Gone, value: {id: x}}\n";

    private OpenAPI run() {
        final OpenAPI api = new OpenAPIV3Parser().readContents(SPEC, null, null).getOpenAPI();
        rule.apply(api, Map.of(), logger);
        return api;
    }

    private static Operation get(final OpenAPI api) {
        return nn(nn(api.getPaths().get("/items/{id}")).getGet());
    }

    @Test
    @DisplayName("a path-item parameter reference takes the description written beside it")
    void pathItemParameterIsOverridden() {
        final OpenAPI api = run();
        final Parameter id = nn(api.getPaths().get("/items/{id}")).getParameters().get(0);
        assertAll(
                () -> assertNull(id.get$ref()),
                () -> assertEquals("id", id.getName()),
                () -> assertEquals("The item to read", id.getDescription()));
    }

    @Test
    @DisplayName("a reference without sibling text is left as a reference")
    void plainReferenceIsKept() {
        assertEquals("#/components/parameters/Limit", get(run()).getParameters().get(0).get$ref());
    }

    @Test
    @DisplayName("a chain of references is followed to the target before the text is applied")
    void chainIsFollowed() {
        final Parameter alias = get(run()).getParameters().get(1);
        assertAll(
                () -> assertEquals("limit", alias.getName()),
                () -> assertEquals("How many to read here", alias.getDescription()));
    }

    @Test
    @DisplayName("a response reference takes the description written beside it")
    void responseIsOverridden() {
        final ApiResponse found = nn(get(run()).getResponses().get("200"));
        assertAll(
                () -> assertNull(found.get$ref()),
                () -> assertEquals("The item that was read", found.getDescription()));
    }

    @Test
    @DisplayName("an example reference takes the summary written beside it")
    void exampleIsOverridden() {
        final Example gone = nn(nn(nn(get(run()).getResponses().get("404")).getContent()
                .get("application/json")).getExamples().get("gone"));
        assertAll(
                () -> assertNull(gone.get$ref()),
                () -> assertEquals("An item that is gone", gone.getSummary()));
    }

    @Test
    @DisplayName("the component keeps its own text for every other use")
    void componentIsUnchanged() {
        final OpenAPI api = run();
        assertAll(
                () -> assertEquals("An id", nn(api.getComponents().getParameters().get("Id")).getDescription()),
                () -> assertEquals("Found it", nn(api.getComponents().getResponses().get("Found")).getDescription()));
    }

    private static <T> T nn(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
