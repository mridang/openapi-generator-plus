package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * Covers the raw-document reader that supplies the Response Object summary, which the parsed model
 * cannot carry.
 */
class RawSpecReaderTest {



    private static Path write(final Path dir, final String yaml) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, yaml);
        return spec;
    }

    @Test
    @DisplayName("a summary on an inline response is read, keyed by the upper-case method and path")
    void readsInlineSummary(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /items:\n"
                                + "    get:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          summary: Items listed\n"
                                + "          description: OK\n");
        assertEquals(
                Map.of("GET /items", Map.of("200", "Items listed")),
                RawSpecReader.responseSummaries(spec.toString()));
    }

    @Test
    @DisplayName("a QUERY operation and a custom method are read, keyed by their method and path")
    void readsQueryAndAdditionalOperations(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /items:\n"
                                + "    query:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          summary: Items found\n"
                                + "          description: OK\n"
                                + "    additionalOperations:\n"
                                + "      LINK:\n"
                                + "        responses:\n"
                                + "          '204':\n"
                                + "            summary: Items linked\n"
                                + "            description: OK\n");
        assertEquals(
                Map.of("QUERY /items", Map.of("200", "Items found"),
                        "LINK /items", Map.of("204", "Items linked")),
                RawSpecReader.responseSummaries(spec.toString()));
    }

    @Test
    @DisplayName("a default response keeps its key and a response without a summary is skipped")
    void keepsDefaultAndSkipsPlainResponses(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /items:\n"
                                + "    post:\n"
                                + "      responses:\n"
                                + "        '201':\n"
                                + "          description: Created\n"
                                + "        default:\n"
                                + "          summary: Request failed\n");
        final Map<String, Map<String, String>> read =
                RawSpecReader.responseSummaries(spec.toString());
        assertEquals(Map.of("POST /items", Map.of("default", "Request failed")), read);
    }

    @Test
    @DisplayName("a blank summary is treated as no summary")
    void blankSummaryIsIgnored(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /items:\n"
                                + "    get:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          summary: '   '\n"
                                + "          description: OK\n");
        assertTrue(RawSpecReader.responseSummaries(spec.toString()).isEmpty());
    }

    @Test
    @DisplayName("a response reached through a reference is not read")
    void referencedResponseIsNotRead(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /items:\n"
                                + "    get:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          $ref: '#/components/responses/Ok'\n");
        assertTrue(RawSpecReader.responseSummaries(spec.toString()).isEmpty());
    }

    @Test
    @DisplayName("a document in JSON is read the same as one in YAML")
    void readsJsonDocument(@TempDir final Path dir) throws IOException {
        final Path spec =
                dir.resolve("openapi.json");
        Files.writeString(
                spec,
                "{\"openapi\":\"3.2.0\",\"paths\":{\"/items\":{\"delete\":{\"responses\":"
                        + "{\"204\":{\"summary\":\"Removed\"}}}}}}");
        final Map<String, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put("DELETE /items", Map.of("204", "Removed"));
        assertEquals(expected, RawSpecReader.responseSummaries(spec.toString()));
    }

    @Test
    @DisplayName("a document with no paths yields no summaries")
    void noPathsYieldsNothing(@TempDir final Path dir) throws IOException {
        assertTrue(RawSpecReader.responseSummaries(write(dir, "openapi: 3.2.0\ninfo:\n  title: T\n").toString()).isEmpty());
    }

    @Test
    @DisplayName("a missing location yields no summaries rather than an error")
    void missingLocationYieldsNothing(@TempDir final Path dir) {
        assertTrue(RawSpecReader.responseSummaries(null).isEmpty());
        assertTrue(RawSpecReader.responseSummaries(dir.resolve("absent.yaml").toString()).isEmpty());
    }

    @Test
    @DisplayName("the summaries of several operations keep document order")
    void keepsOperationsAndCodesInOrder(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "paths:\n"
                                + "  /a:\n"
                                + "    get:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          summary: First\n"
                                + "        '404':\n"
                                + "          summary: Second\n"
                                + "  /b:\n"
                                + "    put:\n"
                                + "      responses:\n"
                                + "        '200':\n"
                                + "          summary: Third\n");
        final Map<String, Map<String, String>> read =
                RawSpecReader.responseSummaries(spec.toString());
        assertEquals(List.of("GET /a", "PUT /b"), List.copyOf(read.keySet()));
        assertEquals(List.of("200", "404"), List.copyOf(Objects.requireNonNull(read.get("GET /a")).keySet()));
    }

    @Test
    @DisplayName("the license identifier is read from the document when the model drops it")
    void readsLicenseIdentifier(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(
                        dir,
                        "openapi: 3.1.0\n"
                                + "info:\n"
                                + "  title: T\n"
                                + "  license:\n"
                                + "    name: Apache 2.0\n"
                                + "    identifier: Apache-2.0\n");
        assertEquals("Apache-2.0", RawSpecReader.licenseIdentifier(spec.toString()));
    }

    @Test
    @DisplayName("a license with no identifier yields none")
    void licenseWithoutIdentifierYieldsNothing(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir, "openapi: 3.1.0\ninfo:\n  license:\n    name: Acme\n");
        assertNull(RawSpecReader.licenseIdentifier(spec.toString()));
    }

    @Test
    @DisplayName("a missing location yields no license identifier")
    void missingLocationYieldsNoLicenseIdentifier() {
        assertNull(RawSpecReader.licenseIdentifier(null));
    }

    @Test
    @DisplayName("a security scheme's deprecated flag, metadata URL and device flow are read")
    void readsSecuritySchemeFields(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir,
                        "openapi: 3.2.0\n"
                                + "components:\n"
                                + "  securitySchemes:\n"
                                + "    old:\n"
                                + "      type: http\n"
                                + "      scheme: basic\n"
                                + "      deprecated: true\n"
                                + "    device:\n"
                                + "      type: oauth2\n"
                                + "      oauth2MetadataUrl: https://id.example.com/.well-known/oauth-authorization-server\n"
                                + "      flows:\n"
                                + "        deviceAuthorization:\n"
                                + "          deviceAuthorizationUrl: https://id.example.com/device\n"
                                + "          tokenUrl: https://id.example.com/token\n"
                                + "          scopes:\n"
                                + "            b: B\n"
                                + "            a: A\n");
        final Map<String, RawSpecReader.RawSecurityScheme> schemes =
                RawSpecReader.securitySchemes(spec.toString());
        final RawSpecReader.RawSecurityScheme old = Objects.requireNonNull(schemes.get("old"));
        assertTrue(old.deprecated());
        assertNull(old.oauth2MetadataUrl());
        assertTrue(!old.hasDeviceFlow());
        final RawSpecReader.RawSecurityScheme device = Objects.requireNonNull(schemes.get("device"));
        assertEquals(
                "https://id.example.com/.well-known/oauth-authorization-server",
                device.oauth2MetadataUrl());
        assertTrue(device.hasDeviceFlow());
        assertEquals("https://id.example.com/device", device.deviceAuthorizationUrl());
        assertEquals("https://id.example.com/token", device.deviceTokenUrl());
        assertNull(device.deviceRefreshUrl());
        assertEquals(List.of("b", "a"), List.copyOf(device.deviceScopes().keySet()));
    }

    @Test
    @DisplayName("a missing location yields no security scheme fields")
    void missingLocationYieldsNoSecuritySchemes() {
        assertTrue(RawSpecReader.securitySchemes(null).isEmpty());
    }
}
