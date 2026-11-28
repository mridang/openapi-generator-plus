package io.github.mridang.codegen.rules;

import io.github.mridang.codegen.OpenApi32ParserExtension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilterOperationsRuleTest {

    private static final Logger logger = LoggerFactory.getLogger(FilterOperationsRuleTest.class);

    private static Operation op(String... tags) {
        Operation operation = new Operation();
        if (tags.length > 0) {
            operation.setTags(List.of(tags));
        }
        return operation;
    }

    private static PathItem item(OpenAPI openAPI, String path) {
        return Objects.requireNonNull(Objects.requireNonNull(openAPI.getPaths()).get(path));
    }

    private static OpenAPI spec() {
        return new OpenAPI()
            .path("/items", new PathItem().get(op("items")).post(op("items", "admin")))
            .path("/items/{id}", new PathItem().get(op("items")).delete(op("admin")))
            .path("/health", new PathItem().get(op()));
    }

    private static void run(String key, FilterOperationsRule.Target target, boolean exclude,
                            String value, OpenAPI openAPI) {
        new FilterOperationsRule(key, target, exclude)
            .apply(openAPI, Map.of(CustomNormalizationRule.RULE_VALUE_KEY, value), logger);
    }

    @Test
    @DisplayName("FILTER_METHODS keeps only the listed methods and drops emptied paths")
    void shouldKeepOnlyListedMethods() {
        OpenAPI openAPI = spec();
        run("FILTER_METHODS", FilterOperationsRule.Target.METHODS, false, "post|DELETE", openAPI);

        assertThat(openAPI).hasPaths("/items", "/items/{id}");
        assertNull(item(openAPI, "/items").getGet());
        assertThat(openAPI).forOperation(SpecAssertions.HttpMethod.POST, "/items");
        assertNull(item(openAPI, "/items/{id}").getGet());
    }

    @Test
    @DisplayName("FILTER_METHODS matches a QUERY operation by its declared method, not its post field")
    void shouldMatchTheDeclaredMethodOfAnOpenApi32Operation() {
        Operation query = op("items");
        query.addExtension(OpenApi32ParserExtension.OPERATION_METHOD, "QUERY");
        OpenAPI openAPI = spec().path("/items QUERY", new PathItem().post(query));
        run("FILTER_METHODS", FilterOperationsRule.Target.METHODS, false, "query", openAPI);

        assertThat(openAPI).hasPaths("/items QUERY");
    }

    @Test
    @DisplayName("EXCLUDE_METHODS removes the listed methods and drops emptied paths")
    void shouldRemoveListedMethods() {
        OpenAPI openAPI = spec();
        run("EXCLUDE_METHODS", FilterOperationsRule.Target.METHODS, true, "get", openAPI);

        assertThat(openAPI).hasPaths("/items", "/items/{id}");
        assertNull(item(openAPI, "/items").getGet());
        assertNull(item(openAPI, "/items/{id}").getGet());
    }

    @Test
    @DisplayName("FILTER_TAGS keeps operations with any listed tag and drops untagged ones")
    void shouldKeepOnlyListedTags() {
        OpenAPI openAPI = spec();
        run("FILTER_TAGS", FilterOperationsRule.Target.TAGS, false, "Admin", openAPI);

        assertThat(openAPI).hasPaths("/items", "/items/{id}");
        assertNull(item(openAPI, "/items").getGet());
        assertNull(item(openAPI, "/items/{id}").getGet());
    }

    @Test
    @DisplayName("EXCLUDE_TAGS removes operations with any listed tag and keeps untagged ones")
    void shouldRemoveListedTags() {
        OpenAPI openAPI = spec();
        run("EXCLUDE_TAGS", FilterOperationsRule.Target.TAGS, true, "admin", openAPI);

        assertThat(openAPI).hasPaths("/items", "/items/{id}", "/health");
        assertNull(item(openAPI, "/items").getPost());
        assertNull(item(openAPI, "/items/{id}").getDelete());
    }

    @Test
    @DisplayName("Should remove a path once its last operation is filtered out")
    void shouldRemoveEmptiedPaths() {
        OpenAPI openAPI = spec();
        run("EXCLUDE_TAGS", FilterOperationsRule.Target.TAGS, true, "admin|items", openAPI);

        assertThat(openAPI).hasPaths("/health");
    }

    @Test
    @DisplayName("Should leave the document alone when the value is empty")
    void shouldSkipOnEmptyValue() {
        OpenAPI openAPI = spec();
        run("FILTER_TAGS", FilterOperationsRule.Target.TAGS, false, "", openAPI);

        assertThat(openAPI).hasPaths("/items", "/items/{id}", "/health");
    }
}
