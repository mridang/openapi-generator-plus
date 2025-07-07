package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.*;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CleanEmptyRequestBodiesRule Tests")
class CleanEmptyRequestBodiesRuleTest {

    private static final Logger logger = LoggerFactory.getLogger(CleanEmptyRequestBodiesRuleTest.class);

    private CleanEmptyRequestBodiesRule rule;
    private OpenAPI openAPI;
    private Map<String, String> removeModeConfig;
    private Map<String, String> tagModeConfig;


    @BeforeEach
    void setUp() {
        rule = new CleanEmptyRequestBodiesRule();
        openAPI = new OpenAPI();
        // Setup reusable configs for different modes
        removeModeConfig = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Remove");
        tagModeConfig = Map.of(CleanEmptyRequestBodiesRule.RULE_VALUE_KEY, "Tag");
    }

    /**
     * Verifies that the rule throws its custom NoPathsException when the apply
     * method is called on an OpenAPI specification where the top-level Paths
     * object is null, preserving the original required behavior.
     */
    @Test
    @DisplayName("Should throw NoPathsException when paths object is null")
    void testApply_whenPathsIsNull_shouldThrowException() {
        openAPI.setPaths(null);
        assertThrows(
            CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger)
        );
    }

    /**
     * Verifies that the rule throws its custom NoPathsException when the
     * Paths object is present but contains no path entries.
     */
    @Test
    @DisplayName("Should throw NoPathsException when paths object is empty")
    void testApply_whenPathsIsEmpty_shouldThrowException() {
        openAPI.setPaths(new Paths());
        assertThrows(
            CleanEmptyRequestBodiesRule.NoPathsException.class,
            () -> rule.apply(openAPI, Collections.emptyMap(), logger)
        );
    }

    /**
     * Tests the "Remove" mode. An optional request body with a null schema
     * is considered empty and should be removed from the operation.
     */
    @Test
    @DisplayName("[Remove Mode] Should remove optional empty body with null schema")
    void testRemoveMode_whenSchemaIsNull_shouldRemoveRequestBody() {
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        Content content = new Content().addMediaType("application/json", new MediaType().schema(null));
        requestBody.setContent(content);
        requestBody.setRequired(false); // Explicitly optional
        operation.setRequestBody(requestBody);
        openAPI.setPaths(new Paths().addPathItem("/test", new PathItem().post(operation)));

        rule.apply(openAPI, removeModeConfig, logger);

        assertNull(operation.getRequestBody());
    }

    /**
     * Tests the "Tag" mode. A required request body with an empty object schema
     * should be tagged with the 'x-is-empty-body' extension and not removed.
     */
    @Test
    @DisplayName("[Tag Mode] Should tag required empty body")
    void testTagMode_whenBodyIsRequiredAndEmpty_shouldTag() {
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>())));
        requestBody.setRequired(true); // Explicitly required
        operation.setRequestBody(requestBody);
        openAPI.setPaths(new Paths().addPathItem("/test", new PathItem().post(operation)));

        rule.apply(openAPI, tagModeConfig, logger); // Run in "Tag" mode

        assertNotNull(operation.getRequestBody());
        Map<String, Object> extensions = operation.getRequestBody().getExtensions();
        assertNotNull(extensions);
        // This is the null-safe way to check the boolean value
        assertEquals(Boolean.TRUE, extensions.get("x-is-empty-body"));
    }

    /**
     * Tests the "Tag" mode. An optional request body with an empty object schema
     * should be ignored and neither tagged nor removed.
     */
    @Test
    @DisplayName("[Tag Mode] Should ignore optional empty body")
    void testTagMode_whenBodyIsOptionalAndEmpty_shouldIgnore() {
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>())));
        requestBody.setRequired(false); // Explicitly optional
        operation.setRequestBody(requestBody);
        openAPI.setPaths(new Paths().addPathItem("/test", new PathItem().post(operation)));

        rule.apply(openAPI, tagModeConfig, logger);

        assertNotNull(operation.getRequestBody());
        assertNull(operation.getRequestBody().getExtensions());
    }

    /**
     * Verifies that the rule can correctly resolve a $ref to an empty schema
     * and tag the request body when it is required.
     */
    @Test
    @DisplayName("[Tag Mode] Should resolve $ref and tag required empty body")
    void testTagMode_withRefToEmptySchema_shouldTag() {
        Schema<?> emptySchema = new Schema<>().type("object").additionalProperties(false);
        openAPI.setComponents(new Components().addSchemas("EmptyRequest", emptySchema));

        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/EmptyRequest"))));
        requestBody.setRequired(true);

        Operation operation = new Operation().requestBody(requestBody);
        openAPI.setPaths(new Paths().addPathItem("/test", new PathItem().post(operation)));

        rule.apply(openAPI, tagModeConfig, logger);

        Map<String, Object> extensions = operation.getRequestBody().getExtensions();
        assertNotNull(extensions);
        // This is the null-safe way to check the boolean value
        assertEquals(Boolean.TRUE, extensions.get("x-is-empty-body"));
    }

    /**
     * Verifies that the rule can correctly resolve a $ref to an empty schema
     * and remove the request body when it is optional and the rule is in
     * "Remove" mode.
     */
    @Test
    @DisplayName("[Remove Mode] Should resolve $ref and remove optional empty body")
    void testRemoveMode_withRefToEmptySchema_shouldRemove() {
        Schema<?> emptySchema = new Schema<>().type("object").additionalProperties(false);
        openAPI.setComponents(new Components().addSchemas("EmptyRequest", emptySchema));

        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/EmptyRequest"))));
        requestBody.setRequired(false); // Optional

        Operation operation = new Operation().requestBody(requestBody);
        openAPI.setPaths(new Paths().addPathItem("/test", new PathItem().post(operation)));

        rule.apply(openAPI, removeModeConfig, logger);

        assertNull(operation.getRequestBody());
    }
}
