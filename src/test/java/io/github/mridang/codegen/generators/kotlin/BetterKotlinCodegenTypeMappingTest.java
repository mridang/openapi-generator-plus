package io.github.mridang.codegen.generators.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Validates Bucket 4.8 ({@code format:time} -&gt; {@code LocalTime},
 * {@code format:duration} -&gt; {@code Duration}) and Bucket 3.1 (API-key
 * header harvesting) in {@link BetterKotlinCodegen}. Both behaviours are
 * pure codegen settings — no generation roundtrip is needed.
 */
class BetterKotlinCodegenTypeMappingTest {

    /* Bucket 4.8 — typeMapping + importMapping for java.time.LocalTime. */
    @Test
    void timeFormatMapsToLocalTime() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("LocalTime", codegen.typeMapping().get("time"));
        assertEquals("java.time.LocalTime", codegen.importMapping().get("LocalTime"));
    }

    /* Bucket 4.8 — typeMapping + importMapping for java.time.Duration. */
    @Test
    void durationFormatMapsToDuration() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        assertEquals("Duration", codegen.typeMapping().get("duration"));
        assertEquals("java.time.Duration", codegen.importMapping().get("Duration"));
    }

    /*
     * Bucket 3.1 — apiKey-in-header security schemes are harvested at
     * processOpenAPI time and exposed as `apiKeyHeaderNames`. The triple
     * Authorization/Cookie/Proxy-Authorization is always present at runtime
     * via the SENSITIVE_HEADER_NAMES constant in DefaultApiClient.kt;
     * this codegen-side test asserts that custom names are surfaced for
     * the template to merge.
     */
    @Test
    void apiKeyInHeaderSchemesAreHarvested() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();

        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        SecurityScheme headerKey =
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key");
        SecurityScheme queryKey =
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.QUERY)
                        .name("api_key");
        SecurityScheme bearer =
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer");
        components.addSecuritySchemes("headerKey", headerKey);
        components.addSecuritySchemes("queryKey", queryKey);
        components.addSecuritySchemes("bearer", bearer);
        openAPI.setComponents(components);

        codegen.processOpenAPI(openAPI);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> harvested =
                (List<Map<String, String>>)
                        codegen.additionalProperties().get("apiKeyHeaderNames");
        assertNotNull(harvested);
        assertEquals(
                1,
                harvested.size(),
                "only apiKey schemes with in=header must be harvested");
        assertEquals("X-Api-Key", harvested.get(0).get("name"));
        assertEquals(
                "x-api-key",
                harvested.get(0).get("lowerName"),
                "lowercased mirror must be emitted for the runtime sensitive-header set");
        Boolean flag = (Boolean) codegen.additionalProperties().get("hasApiKeyHeaderNames");
        assertNotNull(flag);
        assertTrue(flag);
    }

    @Test
    void apiKeyHarvestDeduplicatesByLowercase() {
        BetterKotlinCodegen codegen = new BetterKotlinCodegen();
        OpenAPI openAPI = new OpenAPI();
        Components components = new Components();
        components.addSecuritySchemes(
                "k1",
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Token"));
        components.addSecuritySchemes(
                "k2",
                new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-token"));
        openAPI.setComponents(components);

        codegen.processOpenAPI(openAPI);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> harvested =
                (List<Map<String, String>>)
                        codegen.additionalProperties().get("apiKeyHeaderNames");
        assertNotNull(harvested);
        assertEquals(1, harvested.size(), "duplicate (case-insensitive) names must dedupe");
    }
}
