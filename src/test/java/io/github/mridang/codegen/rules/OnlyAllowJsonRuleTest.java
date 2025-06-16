package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnlyAllowJsonRule Tests (No Mocks)")
class OnlyAllowJsonRuleTest {

    private static final Logger logger = LoggerFactory.getLogger(OnlyAllowJsonRuleTest.class);

    private OnlyAllowJsonRule rule;
    private OpenAPI openAPI;
    private Operation operation;

    @BeforeEach
    void setUp() {
        rule = new OnlyAllowJsonRule();
        openAPI = new OpenAPI();
        operation = new Operation().operationId("testOp");

        // Set up a basic path structure
        PathItem pathItem = new PathItem().post(operation);
        Paths paths = new Paths();
        paths.addPathItem("/test", pathItem);
        openAPI.setPaths(paths);
    }

    @Test
    @DisplayName("Should remove non-JSON content types from RequestBody")
    void testApply_removesNonJsonFromRequestBody() {
        // Given
        Content content = new Content()
            .addMediaType("application/json", new MediaType())
            .addMediaType("application/xml", new MediaType())
            .addMediaType("text/plain", new MediaType());
        RequestBody requestBody = new RequestBody().content(content);
        operation.setRequestBody(requestBody);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        RequestBody resultContent = operation.getRequestBody();
        assertNotNull(resultContent, "Request body should still exist");
        assertNotNull(resultContent.getContent(), "Content in request body should not be null");
        assertEquals(1, resultContent.getContent().size(), "Only one content type should remain");
        assertTrue(resultContent.getContent().containsKey("application/json"), "application/json should be present");
        assertFalse(resultContent.getContent().containsKey("application/xml"), "application/xml should be removed");
    }

    @Test
    @DisplayName("Should remove RequestBody entirely if it becomes empty after filtering")
    void testApply_removesRequestBodyIfItBecomesEmpty() {
        // Given
        Content content = new Content()
            .addMediaType("application/xml", new MediaType())
            .addMediaType("text/plain", new MediaType());
        operation.setRequestBody(new RequestBody().content(content));

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        assertNull(operation.getRequestBody(), "Request body should be null after all content types are removed");
    }

    @Test
    @DisplayName("Should remove non-JSON content types from ApiResponses")
    void testApply_removesNonJsonFromResponses() {
        // Given
        Content content = new Content()
            .addMediaType("application/json", new MediaType())
            .addMediaType("application/xml", new MediaType());
        ApiResponse apiResponse = new ApiResponse().description("A response").content(content);
        operation.setResponses(new ApiResponses().addApiResponse("200", apiResponse));

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        ApiResponses resultResponses = operation.getResponses();
        assertNotNull(resultResponses, "ApiResponses object should not be null");
        ApiResponse resultResponse = resultResponses.get("200");
        assertNotNull(resultResponse, "The 200 response should not be null");
        Content resultContent = resultResponse.getContent();
        assertNotNull(resultContent, "Content in response should not be null");

        assertEquals(1, resultContent.size(), "Only one content type should remain in response");
        assertTrue(resultContent.containsKey("application/json"), "Response should contain application/json");
    }

    @Test
    @DisplayName("Should make response content empty but not remove the response itself")
    void testApply_emptiesResponseContentWithoutRemovingResponse() {
        // Given
        Content content = new Content().addMediaType("application/xml", new MediaType());
        ApiResponse apiResponse = new ApiResponse().description("A response").content(content);
        operation.setResponses(new ApiResponses().addApiResponse("204", apiResponse));

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        ApiResponses resultResponses = operation.getResponses();
        assertNotNull(resultResponses);
        ApiResponse resultResponse = resultResponses.get("204");
        assertNotNull(resultResponse, "The ApiResponse object itself should not be removed");
        assertNotNull(resultResponse.getContent(), "Content object should exist");
        assertTrue(resultResponse.getContent().isEmpty(), "Response content should be empty");
    }

    @Test
    @DisplayName("Should throw NoPathsException if paths are empty")
    void testApply_whenNoPathsExist_shouldThrowException() {
        // Given
        openAPI.setPaths(new Paths()); // Empty paths

        // Then
        assertThrows(
            OnlyAllowJsonRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger),
            "Should throw NoPathsException because validation runs on empty paths"
        );
    }

    @Test
    @DisplayName("Should throw NoPathsException if paths object is null")
    void testApply_whenPathsIsNull_shouldThrowException() {
        // Given
        openAPI.setPaths(null);

        // Then
        assertThrows(
            OnlyAllowJsonRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger),
            "Should throw NoPathsException because validation runs on null paths"
        );
    }

    @Test
    @DisplayName("Should run without error for operations with no content")
    void testApply_withNoContent_shouldNotFail() {
        // Given
        Operation opWithNoContent = new Operation();
        opWithNoContent.setRequestBody(null);
        opWithNoContent.setResponses(new ApiResponses()
            .addApiResponse("204", new ApiResponse().description("No Content")));

        Paths paths = openAPI.getPaths();
        assertNotNull(paths);
        PathItem pathItem = paths.get("/test");
        assertNotNull(pathItem);
        pathItem.setGet(opWithNoContent);

        // When & Then
        assertDoesNotThrow(
            () -> rule.apply(openAPI, Collections.emptyMap(), logger),
            "Rule should execute without error for operations with no content types"
        );
    }

    @Test
    @DisplayName("Should correctly process a mix of valid and invalid operations")
    void testApply_withMixedOperations_shouldProcessCorrectly() {
        // Given a second operation to modify
        Operation opToFilter = new Operation();
        Content requestContent = new Content().addMediaType("application/xml", new MediaType());
        Content responseContent = new Content().addMediaType("application/json", new MediaType()).addMediaType("application/xml", new MediaType());
        opToFilter.setRequestBody(new RequestBody().content(requestContent));
        opToFilter.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse().content(responseContent)));

        Paths paths = openAPI.getPaths();
        assertNotNull(paths);
        PathItem pathItem = paths.get("/test");
        assertNotNull(pathItem);
        pathItem.setPut(opToFilter);

        // When
        rule.apply(openAPI, Collections.emptyMap(), logger);

        // Then
        // Check the first operation (which had no content)
        assertNotNull(openAPI.getPaths());
        assertNotNull(openAPI.getPaths().get("/test"));
        assertNull(openAPI.getPaths().get("/test").getPost().getRequestBody());

        // Check the second operation (which was filtered)
        Operation filteredOp = openAPI.getPaths().get("/test").getPut();
        assertNotNull(filteredOp);
        assertNull(filteredOp.getRequestBody(), "Request body of second op should be removed");

        ApiResponses filteredResponses = filteredOp.getResponses();
        assertNotNull(filteredResponses);
        ApiResponse filtered200Response = filteredResponses.get("200");
        assertNotNull(filtered200Response);
        Content filteredResponseContent = filtered200Response.getContent();
        assertNotNull(filteredResponseContent);

        assertEquals(1, filteredResponseContent.size(), "Response content of second op should be filtered");
        assertTrue(filteredResponseContent.containsKey("application/json"), "Only JSON should remain in response of second op");
    }
}
