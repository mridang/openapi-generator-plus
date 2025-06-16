package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilterPathsRule Tests (No Mocks)")
class FilterPathsRuleTest {

    private static final Logger logger = LoggerFactory.getLogger(FilterPathsRuleTest.class);

    private FilterPathsRule rule;
    private OpenAPI openAPI;
    private Paths originalPaths;

    @BeforeEach
    void setUp() {
        rule = new FilterPathsRule();
        openAPI = new OpenAPI();
        originalPaths = new Paths();

        originalPaths.addPathItem("/api/v1/users", new PathItem());
        originalPaths.addPathItem("/api/v1/products", new PathItem());
        originalPaths.addPathItem("/internal/status", new PathItem());
        originalPaths.addPathItem("/api/v2/users/{id}", new PathItem());

        openAPI.setPaths(originalPaths);
    }

    @Test
    @DisplayName("Should do nothing if regex value is not provided in config")
    void testApply_whenNoRegexProvided_shouldSkipFiltering() {

        Map<String, String> emptyConfig = Collections.emptyMap();
        int originalSize = openAPI.getPaths().size();

        rule.apply(openAPI, emptyConfig, logger);

        assertNotNull(openAPI.getPaths(), "Paths should not be null");
        assertEquals(originalSize, openAPI.getPaths().size(), "Paths should not have been filtered");
    }

    @Test
    @DisplayName("Should do nothing if regex value is empty in config")
    void testApply_whenRegexIsEmpty_shouldSkipFiltering() {

        Map<String, String> configWithEmptyValue = new HashMap<>();
        configWithEmptyValue.put(FilterPathsRule.RULE_VALUE_KEY, "");
        int originalSize = openAPI.getPaths().size();

        rule.apply(openAPI, configWithEmptyValue, logger);

        assertNotNull(openAPI.getPaths());
        assertEquals(originalSize, openAPI.getPaths().size(), "Paths should not have been filtered for empty regex");
    }

    @Test
    @DisplayName("Should throw NoPathsException when paths object is null")
    void testApply_whenPathsIsNull_shouldThrowNoPathsException() {

        openAPI.setPaths(null);
        Map<String, String> config = Collections.singletonMap(FilterPathsRule.RULE_VALUE_KEY, "/api/.*");

        FilterPathsRule.NoPathsException exception = assertThrows(
            FilterPathsRule.NoPathsException.class,
            () -> rule.apply(openAPI, config, logger)
        );
        assertEquals(
            "Error: Paths object is null or empty, cannot perform route regex filtering.",
            exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should throw NoPathsException when paths object is empty")
    void testApply_whenPathsIsEmpty_shouldThrowNoPathsException() {

        openAPI.setPaths(new Paths()); // Empty paths
        Map<String, String> config = Collections.singletonMap(FilterPathsRule.RULE_VALUE_KEY, "/api/.*");

        assertThrows(
            FilterPathsRule.NoPathsException.class,
            () -> rule.apply(openAPI, config, logger)
        );
    }

    @Test
    @DisplayName("Should keep only paths that match a single regex")
    void testApply_shouldKeepMatchingPaths() {


        Map<String, String> config = Collections.singletonMap(FilterPathsRule.RULE_VALUE_KEY, "/api/v1/.*");

        rule.apply(openAPI, config, logger);

        Paths filteredPaths = openAPI.getPaths();
        assertEquals(2, filteredPaths.size(), "Should only keep the two v1 paths");
        assertTrue(filteredPaths.containsKey("/api/v1/users"), "Should contain /api/v1/users");
        assertTrue(filteredPaths.containsKey("/api/v1/products"), "Should contain /api/v1/products");
        assertFalse(filteredPaths.containsKey("/internal/status"), "Should not contain /internal/status");
        assertFalse(filteredPaths.containsKey("/api/v2/users/{id}"), "Should not contain v2 path");
    }

    @Test
    @DisplayName("Should keep paths that match any of multiple comma-separated regexes")
    void testApply_withMultipleRegexPatterns() {


        String regex = ".*/users.*,/internal/status";
        Map<String, String> config = Collections.singletonMap(FilterPathsRule.RULE_VALUE_KEY, regex);

        rule.apply(openAPI, config, logger);

        Paths filteredPaths = openAPI.getPaths();
        assertEquals(3, filteredPaths.size(), "Should keep all three matching paths");
        assertTrue(filteredPaths.containsKey("/api/v1/users"), "Should contain v1 users path");
        assertTrue(filteredPaths.containsKey("/api/v2/users/{id}"), "Should contain v2 users path");
        assertTrue(filteredPaths.containsKey("/internal/status"), "Should contain status path");
        assertFalse(filteredPaths.containsKey("/api/v1/products"), "Should not contain products path");
    }

    @Test
    @DisplayName("Should result in empty paths if no path matches the regex")
    void testApply_whenNoPathsMatch_shouldResultInEmptyPaths() {

        Map<String, String> config = Collections.singletonMap(FilterPathsRule.RULE_VALUE_KEY, "/nonexistent/.*");

        rule.apply(openAPI, config, logger);

        Paths filteredPaths = openAPI.getPaths();
        assertNotNull(filteredPaths, "Paths object should not be null");
        assertTrue(filteredPaths.isEmpty(), "Paths should be empty as no path matched");
    }
}
