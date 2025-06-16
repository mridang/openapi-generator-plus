package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CleanEmptyRequestBodiesRule Tests (No Mocks)")
class CleanEmptyRequestBodiesRuleTest {

    // Using a standard logger; you can check console output or use more advanced test appenders
    private static final Logger logger = LoggerFactory.getLogger(CleanEmptyRequestBodiesRuleTest.class);

    private CleanEmptyRequestBodiesRule rule;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        rule = new CleanEmptyRequestBodiesRule();
        openAPI = new OpenAPI();
    }

    @Test
    @DisplayName("Should throw NoPathsException when paths object is null")
    void testApply_whenPathsIsNull_shouldThrowException() {
        // Given
        openAPI.setPaths(null);

        // When & Then
        CleanEmptyRequestBodiesRule.NoPathsException exception = assertThrows(
            CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger)
        );

        assertEquals(
            "Error: Paths object is null or empty, cannot process request bodies for empty request body removal.",
            exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should throw NoPathsException when paths object is empty")
    void testApply_whenPathsIsEmpty_shouldThrowException() {
        // Given
        openAPI.setPaths(new Paths()); // Empty paths

        // When & Then
        assertThrows(
            CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger)
        );
    }

    @Test
    @DisplayName("Should remove request body if its content is null")
    void testApply_whenRequestBodyContentIsNull_shouldRemoveRequestBody() {
        // Given
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(null); // Content is null
        operation.setRequestBody(requestBody);

        PathItem pathItem = new PathItem().post(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNull(operation.getRequestBody(), "Request body should have been removed");
    }

    @Test
    @DisplayName("Should remove request body if its content is empty")
    void testApply_whenRequestBodyContentIsEmpty_shouldRemoveRequestBody() {
        // Given
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content()); // Content is empty
        operation.setRequestBody(requestBody);

        PathItem pathItem = new PathItem().post(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNull(operation.getRequestBody(), "Request body should have been removed");
    }

    @Test
    @DisplayName("Should remove request body if media type schema is null")
    void testApply_whenSchemaIsNull_shouldRemoveRequestBody() {
        // Given
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(null); // Schema is null
        content.addMediaType("application/json", mediaType);
        requestBody.setContent(content);
        operation.setRequestBody(requestBody);

        PathItem pathItem = new PathItem().put(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNull(operation.getRequestBody(), "Request body with null schema should be removed");
    }

    @Test
    @DisplayName("Should keep request body if it has a meaningful schema")
    void testApply_whenSchemaIsPresent_shouldKeepRequestBody() {
        // Given
        Operation operation = new Operation();
        RequestBody originalRequestBody = new RequestBody();
        Content content = new Content();
        MediaType mediaType = new MediaType();
        mediaType.setSchema(new Schema<>()); // A non-null schema
        content.addMediaType("application/json", mediaType);
        originalRequestBody.setContent(content);
        operation.setRequestBody(originalRequestBody);

        PathItem pathItem = new PathItem().get(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNotNull(operation.getRequestBody(), "Request body should be kept");
        assertSame(originalRequestBody, operation.getRequestBody(), "Request body should not be changed");
    }

    @Test
    @DisplayName("Should correctly process multiple operations")
    void testApply_withMultipleOperations_shouldProcessAll() {
        // Given
        // Operation 1: Empty request body
        Operation opWithEmptyBody = new Operation();
        RequestBody emptyRequestBody = new RequestBody();
        emptyRequestBody.setContent(new Content());
        opWithEmptyBody.setRequestBody(emptyRequestBody);

        // Operation 2: Valid request body
        Operation opWithValidBody = new Operation();
        RequestBody validRequestBody = new RequestBody();
        Content content = new Content();
        content.addMediaType("application/json", new MediaType().schema(new Schema<>()));
        validRequestBody.setContent(content);
        opWithValidBody.setRequestBody(validRequestBody);

        PathItem pathItem = new PathItem().post(opWithEmptyBody).get(opWithValidBody);
        Paths paths = new Paths();
        paths.addPathItem("/multitest", pathItem);
        openAPI.setPaths(paths);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNull(opWithEmptyBody.getRequestBody(), "Empty request body should have been removed");
        assertNotNull(opWithValidBody.getRequestBody(), "Valid request body should be kept");
    }

    @Test
    @DisplayName("Should not fail if an operation has no request body")
    void testApply_whenOperationHasNoRequestBody_shouldNotFail() {
        // Given
        Operation operation = new Operation();
        operation.setRequestBody(null); // No request body to begin with

        PathItem pathItem = new PathItem().delete(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);

        // When & Then
        assertDoesNotThrow(
            () -> rule.apply(openAPI, Collections.emptyMap(), logger),
            "Rule should execute without error for operations lacking a request body"
        );
        assertNull(operation.getRequestBody(), "Request body should remain null");
    }
}
