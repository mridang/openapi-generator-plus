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
}
