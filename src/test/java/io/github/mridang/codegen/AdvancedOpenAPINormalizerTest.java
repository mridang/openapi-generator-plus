package io.github.mridang.codegen;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.HttpMethod;
import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;

@DisplayName("AdvancedOpenAPINormalizer Tests")
class AdvancedOpenAPINormalizerTest {

    @Test
    @DisplayName("Should apply only the rules specified in the configuration")
    void shouldApplyOnlySpecifiedRules() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components()
                .addParameters("UnusedParameter", new Parameter())
            )
            .path("/api/v1/users", new PathItem().get(new Operation()
                .addParametersItem(new Parameter().name("paramToStrip"))
                .addParametersItem(new Parameter().name("paramToKeep"))
            ))
            .path("/internal/status", new PathItem().get(new Operation()));

        Map<String, String> rulesToRun = Map.of(
            "STRIP_PARAMS", "paramToStrip",
            "FILTER_PATHS", "/api/.*"
        );

        AdvancedOpenAPINormalizer normalizer = new AdvancedOpenAPINormalizer(openAPI, rulesToRun);
        normalizer.normalize();

        assertThat(openAPI)
            .hasPaths("/api/v1/users")
            .hasParameterComponents("UnusedParameter")
            .forOperation(HttpMethod.GET, "/api/v1/users")
            .hasParameters("paramToKeep");
    }

    @Test
    @DisplayName("Should wire the method and tag filters, with exclusions winning")
    void shouldApplyOperationFilters() {
        OpenAPI openAPI = new OpenAPI()
            .path("/a", new PathItem()
                .get(new Operation().addTagsItem("one"))
                .post(new Operation().addTagsItem("one")))
            .path("/b", new PathItem().get(new Operation().addTagsItem("two")))
            .path("/c", new PathItem().get(new Operation().addTagsItem("one").addTagsItem("two")));

        Map<String, String> rulesToRun = Map.of(
            "FILTER_METHODS", "get",
            "FILTER_TAGS", "one",
            "EXCLUDE_TAGS", "two"
        );

        new AdvancedOpenAPINormalizer(openAPI, rulesToRun).normalize();

        assertThat(openAPI).hasPaths("/a");
        org.junit.jupiter.api.Assertions.assertNull(java.util.Objects.requireNonNull(
            java.util.Objects.requireNonNull(openAPI.getPaths()).get("/a")).getPost());
    }

    @Test
    @DisplayName("Keeps a dangling $ref lenient by default and fails it when strictSpec is set")
    void strictSpecFlagControlsDanglingRefs() {
        OpenAPI openAPI = new OpenAPI().path("/pets", new PathItem().get(new Operation()
            .responses(new io.swagger.v3.oas.models.responses.ApiResponses().addApiResponse("200",
                new io.swagger.v3.oas.models.responses.ApiResponse().content(
                    new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType().schema(
                            new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/Missing"))))))));

        new AdvancedOpenAPINormalizer(openAPI, Map.of()).normalize();

        try {
            System.setProperty(SpecWarnings.STRICT_PROPERTY, "true");
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new AdvancedOpenAPINormalizer(openAPI, Map.of()).normalize());
        } finally {
            System.clearProperty(SpecWarnings.STRICT_PROPERTY);
        }
    }
}
