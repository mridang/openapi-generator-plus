package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.HttpMethod;
import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;

class OnlyAllowJsonRuleTest extends BaseRuleTest<OnlyAllowJsonRule> {

    @Test
    @DisplayName("Should remove non-JSON content types from RequestBody")
    void shouldRemoveNonJsonFromRequestBody() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .requestBody(new RequestBody().content(new Content()
                .addMediaType("application/json", new MediaType())
                .addMediaType("application/xml", new MediaType())))));

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(body -> body.hasContentTypes("application/json"));
    }

    @Test
    @DisplayName("Should remove RequestBody if it becomes empty after filtering")
    void shouldRemoveRequestBodyIfEmpty() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .requestBody(new RequestBody().content(new Content()
                .addMediaType("application/xml", new MediaType())))));

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isNull);
    }

    @Test
    @DisplayName("Should remove non-JSON content types from ApiResponses")
    void shouldRemoveNonJsonFromResponses() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                .content(new Content()
                    .addMediaType("application/json", new MediaType())
                    .addMediaType("application/xml", new MediaType()))))));

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasResponses(responses -> responses
                .code("200", response -> response.hasContentTypes("application/json"))
            );
    }
}
