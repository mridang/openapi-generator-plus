package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.HttpMethod;
import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CleanEmptyRequestBodiesRuleTest extends BaseRuleTest<CleanEmptyRequestBodiesRule> {

    @Test
    @DisplayName("Should throw NoPathsException when paths object is null")
    void shouldThrowOnNullPaths() {
        OpenAPI openAPI = new OpenAPI().paths(null);
        assertThrows(CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Map.of(), logger));
    }

    @Test
    @DisplayName("Should throw NoPathsException when paths object is empty")
    void shouldThrowOnEmptyPaths() {
        OpenAPI openAPI = new OpenAPI().paths(new Paths());
        assertThrows(CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Map.of(), logger));
    }

    @Test
    @DisplayName("[Remove Mode] Should remove optional empty body")
    void removeModeShouldRemoveOptionalEmptyBody() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .requestBody(new RequestBody().required(false)
                .content(new Content().addMediaType("application/json", new MediaType().schema(null))))));
        Map<String, String> config = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Remove");

        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isNull);
    }

    @Test
    @DisplayName("[Tag Mode] Should tag required empty body")
    void tagModeShouldTagRequiredEmptyBody() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .requestBody(new RequestBody().required(true)
                .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>()))))));
        Map<String, String> config = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Tag");

        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isTaggedAsEmpty);
    }

    @Test
    @DisplayName("[Tag Mode] Should ignore optional empty body")
    void tagModeShouldIgnoreOptionalEmptyBody() {
        OpenAPI openAPI = new OpenAPI().path("/test", new PathItem().post(new Operation()
            .requestBody(new RequestBody().required(false)
                .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>()))))));
        Map<String, String> config = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Tag");

        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isNotTagged);
    }

    @Test
    @DisplayName("[Tag Mode] Should resolve $ref and tag required empty body")
    void tagModeShouldTagRefdEmptyBody() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components().addSchemas("EmptyRequest", new Schema<>().type("object")))
            .path("/test", new PathItem().post(new Operation()
                .requestBody(new RequestBody().required(true)
                    .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/EmptyRequest")))))));
        Map<String, String> config = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Tag");

        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isTaggedAsEmpty);
    }

    @Test
    @DisplayName("[Remove Mode] Should resolve $ref and remove optional empty body")
    void removeModeShouldRemoveRefdEmptyBody() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components().addSchemas("EmptyRequest", new Schema<>().type("object")))
            .path("/test", new PathItem().post(new Operation()
                .requestBody(new RequestBody().required(false)
                    .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/EmptyRequest")))))));
        Map<String, String> config = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Remove");

        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .forOperation(HttpMethod.POST, "/test")
            .hasRequestBody(SpecAssertions.RequestBodyAsserter::isNull);
    }
}
