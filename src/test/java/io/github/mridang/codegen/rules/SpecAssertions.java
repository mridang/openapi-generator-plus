package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public final class SpecAssertions {

    public static OpenAPIAsserter assertThat(OpenAPI openAPI) {
        return new OpenAPIAsserter(openAPI);
    }

    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class OpenAPIAsserter {
        private final OpenAPI actual;

        private OpenAPIAsserter(OpenAPI openAPI) {
            assertNotNull(openAPI, "The OpenAPI spec cannot be null.");
            this.actual = openAPI;
        }

        public OpenAPIAsserter hasPaths(String... expectedPaths) {
            Paths paths = actual.getPaths();
            assertNotNull(paths, "Paths object should not be null.");
            assertEquals(new HashSet<>(Arrays.asList(expectedPaths)), paths.keySet());
            return this;
        }

        public OpenAPIAsserter hasParameterComponents(String... expectedNames) {
            assertNotNull(actual.getComponents(), "Components section should not be null.");
            assertNotNull(actual.getComponents().getParameters(), "Component parameters map should not be null.");
            assertEquals(new HashSet<>(Arrays.asList(expectedNames)), actual.getComponents().getParameters().keySet());
            return this;
        }

        /**
         * Asserts that the spec's components contain exactly the given schema names.
         *
         * @param expectedNames The expected schema component names.
         * @return This asserter for chaining.
         */
        public OpenAPIAsserter hasSchemaComponents(String... expectedNames) {
            assertNotNull(actual.getComponents(), "Components section should not be null.");
            assertNotNull(actual.getComponents().getSchemas(), "Component schemas map should not be null.");
            assertEquals(new HashSet<>(Arrays.asList(expectedNames)), actual.getComponents().getSchemas().keySet());
            return this;
        }

        public OperationAsserter forOperation(HttpMethod method, String path) {
            Paths paths = actual.getPaths();
            assertNotNull(paths, "Paths should not be null");
            PathItem pathItem = paths.get(path);
            assertNotNull(pathItem, "PathItem for '" + path + "' should not be null");

            Operation operation;
            switch (method) {
                case GET:
                    operation = pathItem.getGet();
                    break;
                case POST:
                    operation = pathItem.getPost();
                    break;
                case PUT:
                    operation = pathItem.getPut();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method for test: " + method);
            }
            assertNotNull(operation, "Operation for " + method + " " + path + " should not be null");

            return new OperationAsserter(operation, this);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class OperationAsserter {
        private final Operation actual;
        private final OpenAPIAsserter parent;

        private OperationAsserter(Operation operation, OpenAPIAsserter parent) {
            this.actual = operation;
            this.parent = parent;
        }

        public OperationAsserter hasParameters(String... expectedNames) {
            List<Parameter> params = actual.getParameters();
            assertNotNull(params, "Operation parameter list should not be null");

            Set<String> actualNames = params.stream().map(p -> {
                if (p.get$ref() != null) {
                    return p.get$ref().substring(p.get$ref().lastIndexOf('/') + 1);
                }
                return p.getName();
            }).collect(Collectors.toSet());

            assertEquals(new HashSet<>(Arrays.asList(expectedNames)), actualNames);
            return this;
        }

        public OperationAsserter hasRequestBody(Consumer<RequestBodyAsserter> consumer) {
            consumer.accept(new RequestBodyAsserter(actual.getRequestBody()));
            return this;
        }

        public OperationAsserter hasResponses(Consumer<ApiResponsesAsserter> consumer) {
            consumer.accept(new ApiResponsesAsserter(actual.getResponses()));
            return this;
        }

        public OpenAPIAsserter and() {
            return this.parent;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class RequestBodyAsserter {
        private final RequestBody actual;

        private RequestBodyAsserter(RequestBody actual) {
            this.actual = actual;
        }

        public void isNull() {
            assertNull(actual, "Expected RequestBody to be null.");
        }

        public void isNotNull() {
            assertNotNull(actual, "Expected RequestBody not to be null.");
        }

        public RequestBodyAsserter isTaggedAsEmpty() {
            isNotNull();
            Map<String, Object> extensions = actual.getExtensions();
            assertNotNull(extensions, "Expected extensions map, but it was null.");
            assertEquals(true, extensions.get("x-is-empty-body"), "Expected 'x-is-empty-body' extension to be true.");
            return this;
        }

        public RequestBodyAsserter isNotTagged() {
            isNotNull();
            assertTrue(
                actual.getExtensions() == null || !actual.getExtensions().containsKey("x-is-empty-body"),
                "Expected request body to not have the 'x-is-empty-body' tag."
            );
            return this;
        }

        public void hasContentTypes(String... expectedTypes) {
            isNotNull();
            Content content = actual.getContent();
            assertNotNull(content, "Expected request body to have content, but it was null.");
            assertEquals(new HashSet<>(Arrays.asList(expectedTypes)), content.keySet());
        }
    }

    public static class ApiResponsesAsserter {
        private final ApiResponses actual;

        private ApiResponsesAsserter(ApiResponses actual) {
            assertNotNull(actual, "The ApiResponses object cannot be null.");
            this.actual = actual;
        }

        public ApiResponsesAsserter code(String statusCode, Consumer<ApiResponseAsserter> consumer) {
            ApiResponse response = actual.get(statusCode);
            assertNotNull(response, "Expected a response for status code '" + statusCode + "', but it was missing.");
            consumer.accept(new ApiResponseAsserter(response));
            return this;
        }
    }

    public static class ApiResponseAsserter {
        private final ApiResponse actual;

        private ApiResponseAsserter(ApiResponse actual) {
            this.actual = actual;
        }

        public void hasNoContent() {
            assertTrue(actual.getContent() == null || actual.getContent().isEmpty());
        }

        public void hasContentTypes(String... expectedTypes) {
            Content content = actual.getContent();
            assertNotNull(content, "Expected response to have content, but it was null.");
            assertEquals(new HashSet<>(Arrays.asList(expectedTypes)), content.keySet());
        }
    }
}
