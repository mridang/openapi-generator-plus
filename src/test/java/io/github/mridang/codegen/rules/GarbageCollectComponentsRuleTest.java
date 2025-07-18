package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class GarbageCollectComponentsRuleTest extends BaseRuleTest<GarbageCollectComponentsRule> {

    @Test
    @DisplayName("Should remove unused schema and parameter components")
    public void shouldRemoveUnusedComponents() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components()
                .addSchemas("UsedSchema", new Schema<>())
                .addSchemas("UnusedSchema", new Schema<>())
                .addParameters("UsedParameter", new Parameter())
                .addParameters("UnusedParameter", new Parameter())
            )
            .path("/test", new PathItem()
                .post(new Operation()
                    .requestBody(new RequestBody()
                        .content(new Content()
                            .addMediaType("*/*", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/UsedSchema")))
                        )
                    )
                    .parameters(Collections.singletonList(
                        new Parameter().$ref("#/components/parameters/UsedParameter")
                    ))
                )
            );

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI)
            .hasSchemaComponents("UsedSchema")
            .hasParameterComponents("UsedParameter");
    }

    @Test
    @DisplayName("Should not remove any components when all are referenced")
    public void shouldKeepAllComponentsWhenReferenced() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components()
                .addSchemas("SchemaA", new Schema<>())
                .addParameters("ParamA", new Parameter())
            )
            .path("/test", new PathItem()
                .post(new Operation()
                    .requestBody(new RequestBody()
                        .content(new Content()
                            .addMediaType("*/*", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/SchemaA")))
                        )
                    )
                    .parameters(Collections.singletonList(
                        new Parameter().$ref("#/components/parameters/ParamA")
                    ))
                )
            );

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI)
            .hasSchemaComponents("SchemaA")
            .hasParameterComponents("ParamA");
    }

    @Test
    @DisplayName("Should run without error for spec with null components")
    public void shouldHandleNullComponentsGracefully() {
        OpenAPI openAPI = new OpenAPI();
        assertDoesNotThrow(() -> rule.apply(openAPI, Map.of(), logger));
    }

    @Test
    @DisplayName("Should run without error for spec with empty components")
    public void shouldHandleEmptyComponentsGracefully() {
        OpenAPI openAPI = new OpenAPI().components(new Components());
        assertDoesNotThrow(() -> rule.apply(openAPI, Map.of(), logger));
    }
}
