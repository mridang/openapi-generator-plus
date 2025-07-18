package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;

class FilterPathsRuleTest extends BaseRuleTest<FilterPathsRule> {

    @Test
    @DisplayName("Should keep only paths that match a single regex")
    void shouldKeepMatchingPaths() {
        OpenAPI openAPI = new OpenAPI()
            .path("/api/v1/users", new PathItem())
            .path("/api/v1/products", new PathItem())
            .path("/internal/status", new PathItem())
            .path("/api/v2/users/{id}", new PathItem());

        Map<String, String> config = Map.of(FilterPathsRule.RULE_VALUE_KEY, "/api/v1/.*");
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).hasPaths("/api/v1/users", "/api/v1/products");
    }

    @Test
    @DisplayName("Should keep paths that match any of multiple regexes")
    void shouldHandleMultipleRegex() {
        OpenAPI openAPI = new OpenAPI()
            .path("/api/v1/users", new PathItem())
            .path("/api/v1/products", new PathItem())
            .path("/internal/status", new PathItem())
            .path("/api/v2/users/{id}", new PathItem());
        String regex = ".*/users.*,/internal/status";

        Map<String, String> config = Map.of(FilterPathsRule.RULE_VALUE_KEY, regex);
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).hasPaths("/api/v1/users", "/api/v2/users/{id}", "/internal/status");
    }

    @Test
    @DisplayName("Should result in empty paths if no path matches")
    void shouldResultInEmptyPathsOnNoMatch() {
        OpenAPI openAPI = new OpenAPI()
            .path("/api/v1/users", new PathItem())
            .path("/api/v1/products", new PathItem())
            .path("/internal/status", new PathItem())
            .path("/api/v2/users/{id}", new PathItem());

        Map<String, String> config = Map.of(FilterPathsRule.RULE_VALUE_KEY, "/nonexistent/.*");
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).hasPaths();
    }
}
