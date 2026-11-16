package io.github.mridang.codegen.generators.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.ProbeGenerator;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /* An apiKey scheme name is spliced into a Kotlin string literal, so
     * the quote, backslash and template sigil must be escaped. */
    @Test
    void escapesApiKeyNamesForAKotlinStringLiteral() {
        assertEquals("X-Api'Key", BetterKotlinCodegen.escapeKotlinString("X-Api'Key"));
        assertEquals("X-\\$ident", BetterKotlinCodegen.escapeKotlinString("X-$ident"));
        assertEquals("a\\\"b\\\\c", BetterKotlinCodegen.escapeKotlinString("a\"b\\c"));
    }

    private static final String ALL_OF_DISCRIMINATOR_SPEC =
            String.join(
                    "\n",
                    "openapi: 3.0.3",
                    "info: {title: t, version: '1'}",
                    "paths:",
                    "  /animal:",
                    "    get:",
                    "      operationId: getAnimal",
                    "      responses:",
                    "        '200':",
                    "          description: ok",
                    "          content:",
                    "            application/json:",
                    "              schema: {$ref: '#/components/schemas/Animal'}",
                    "components:",
                    "  schemas:",
                    "    Animal:",
                    "      type: object",
                    "      required: [petType]",
                    "      properties:",
                    "        petType: {type: string}",
                    "        name: {type: string}",
                    "      discriminator:",
                    "        propertyName: petType",
                    "        mapping:",
                    "          cat: '#/components/schemas/Cat'",
                    "          dog: '#/components/schemas/Dog'",
                    "    Cat:",
                    "      allOf:",
                    "        - $ref: '#/components/schemas/Animal'",
                    "        - type: object",
                    "          properties:",
                    "            indoor: {type: boolean}",
                    "    Dog:",
                    "      allOf:",
                    "        - $ref: '#/components/schemas/Animal'",
                    "        - type: object",
                    "          properties:",
                    "            barkVolume: {type: integer, format: int32}",
                    "");

    private static String generated(Path out, String fileName) throws IOException {
        try (Stream<Path> files = Files.walk(out)) {
            final Path found =
                    files.filter(p -> p.getFileName().toString().equals(fileName))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(fileName + " was not generated"));
            return Files.readString(found);
        }
    }

    /*
     * An allOf with a discriminator is a sealed interface for the base and
     * data-class subtypes with the base fields flattened in, never an
     * `open data class` (which Kotlin rejects).
     */
    @Test
    void allOfWithDiscriminatorIsASealedInterfaceWithDataClassSubtypes(@TempDir Path dir)
            throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, ALL_OF_DISCRIMINATOR_SPEC);
        final Path out = dir.resolve("out");
        ProbeGenerator.generate(
                "kotlin-plus",
                Map.of(
                        "modelPackage", "com.example.models",
                        "apiPackage", "com.example.api",
                        "invokerPackage", "com.example"),
                out,
                spec.toString());

        final String animal = generated(out, "Animal.kt");
        assertTrue(animal.contains("sealed interface Animal"), animal);
        assertTrue(animal.contains("\"cat\" ->"), "decoded by the discriminator value");

        final String cat = generated(out, "Cat.kt");
        assertFalse(cat.contains("open data class"), cat);
        assertTrue(cat.contains("data class Cat("), cat);
        assertTrue(cat.contains(") : Animal"), cat);
        assertTrue(cat.contains("override val petType"), "base field flattened and overridden");
        assertTrue(cat.contains("val indoor"), cat);
    }

    /* Required parameters, then server, then the options object. */
    @Test
    void placesTheServerBeforeTheOptionsObject() {
        final Map<String, Object> path = Map.of("paramName", "petId");
        final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);
        final Map<String, Object> server = Map.of("paramName", "server", "isServer", true);

        assertEquals(
                List.of(path, server, options),
                BetterKotlinCodegen.serverBeforeOptions(List.of(path, options, server)));
    }

    @Test
    void keepsASignatureWithoutAServerUnchanged() {
        final Map<String, Object> path = Map.of("paramName", "petId");
        final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);

        assertEquals(
                List.of(path, options),
                BetterKotlinCodegen.serverBeforeOptions(List.of(path, options)));
    }
}
