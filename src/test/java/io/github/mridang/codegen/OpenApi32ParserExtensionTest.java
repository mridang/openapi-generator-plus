package io.github.mridang.codegen;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mridang.codegen.generators.ProbeGenerator;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApi32ParserExtensionTest {

    private static final String FIXTURE = "specs/oas32/openapi.yaml";

    private static String fixturePath() throws URISyntaxException {
        final URL url = Objects.requireNonNull(
                OpenApi32ParserExtensionTest.class.getClassLoader().getResource(FIXTURE));
        return Paths.get(url.toURI()).toString();
    }

    private static <T> T nn(@Nullable final T value) {
        return Objects.requireNonNull(value);
    }

    private static OpenAPI parse(final String location) {
        final ParseOptions options = new ParseOptions();
        options.setResolve(true);
        final SwaggerParseResult result = new OpenAPIParser().readLocation(location, null, options);
        assertNotNull(result, "no parse result");
        assertNotNull(result.getOpenAPI(), () -> "no document: " + result.getMessages());
        return result.getOpenAPI();
    }

    private static MediaType mediaType(final OpenAPI api, final String path, final String code,
                                       final String type) {
        final io.swagger.v3.oas.models.PathItem item = nn(api.getPaths().get(path));
        return nn(nn(item.readOperations().get(0).getResponses().get(code)).getContent().get(type));
    }

    @Test
    @DisplayName("a 3.2.0 document loads and keeps its declared version")
    void loadsAnOpenApi32Document() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        assertAll(
                () -> assertEquals("3.2.0", api.getOpenapi()),
                () -> assertEquals("Ledger Fixture", api.getInfo().getTitle()),
                () -> assertEquals(3, api.getPaths().size()));
    }

    @Test
    @DisplayName("a 3.1 document is left to the stock reader")
    void leavesOtherVersionsAlone() {
        assertNull(new OpenApi32ParserExtension().readContents(
                "openapi: 3.1.0\ninfo: {title: T, version: '1'}\npaths: {}\n", List.of(),
                new ParseOptions()));
    }

    @Test
    @DisplayName("$self moves the base URI that relative references resolve against")
    void selfSetsTheBaseUri() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final MediaType json = mediaType(api, "/ledgers/{code}", "200", "application/json");
        assertTrue(json.getSchema().getProperties() != null
                        && json.getSchema().getProperties().containsKey("balance"),
                "common.yaml was not resolved from the $self folder");
    }

    @Test
    @DisplayName("without $self the same relative reference resolves next to the document")
    void baseIsTheRetrievalLocationWithoutSelf(@TempDir final Path dir) {
        final ObjectNode root = Json.mapper().createObjectNode().put("openapi", "3.2.0");
        final String location = dir.resolve("openapi.yaml").toString();
        assertEquals(location, OpenApi32ParserExtension.baseLocation(root, location));
    }

    @Test
    @DisplayName("a relative $self resolves against the retrieval location")
    void relativeSelfResolvesAgainstTheLocation(@TempDir final Path dir) {
        final ObjectNode root = Json.mapper().createObjectNode()
                .put("openapi", "3.2.0").put("$self", "nested/api.yaml");
        assertEquals(dir.resolve("nested/api.yaml").toString(),
                OpenApi32ParserExtension.baseLocation(root, dir.resolve("openapi.yaml").toString()));
    }

    @Test
    @DisplayName("an absolute $self is the base URI as written")
    void absoluteSelfIsTheBase() {
        final ObjectNode root = Json.mapper().createObjectNode()
                .put("openapi", "3.2.0").put("$self", "https://api.example.com/specs/openapi.yaml");
        assertEquals("https://api.example.com/specs/openapi.yaml",
                OpenApi32ParserExtension.baseLocation(root, "/tmp/openapi.yaml"));
    }

    @Test
    @DisplayName("a reference to the document's own $self URI becomes a local reference")
    void referenceToSelfBecomesLocal(@TempDir final Path dir) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, "openapi: 3.2.0\n"
                + "$self: https://api.example.com/specs/openapi.yaml\n"
                + "info: {title: T, version: '1'}\n"
                + "paths:\n"
                + "  /items:\n"
                + "    get:\n"
                + "      responses:\n"
                + "        '200':\n"
                + "          description: OK\n"
                + "          content:\n"
                + "            application/json:\n"
                + "              schema:\n"
                + "                $ref: 'https://api.example.com/specs/openapi.yaml#/components/schemas/Item'\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Item:\n"
                + "      type: object\n", StandardCharsets.UTF_8);
        final OpenAPI api = parse(spec.toString());
        assertEquals("#/components/schemas/Item",
                mediaType(api, "/items", "200", "application/json").getSchema().get$ref());
    }

    @Test
    @DisplayName("a reusable Media Type Object reads like the inline one")
    void mediaTypeReferenceIsInlined() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final MediaType json = mediaType(api, "/ledgers/{code}", "200", "application/json");
        assertNotNull(json.getSchema(), "the referenced media type has no schema");
    }

    @Test
    @DisplayName("itemSchema becomes the schema of a streamed response")
    void itemSchemaIsTheStreamSchema() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        assertNotNull(mediaType(api, "/ledgers/stream", "200", "application/jsonl").getSchema(),
                "the item schema was not read");
    }

    @Test
    @DisplayName("dataValue and a JSON serializedValue become the example value")
    @SuppressWarnings("unchecked")
    void exampleValuesAreRead() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final MediaType body = nn(nn(api.getPaths().get("/ledgers")).getPost().getRequestBody()
                .getContent().get("application/json"));
        final io.swagger.v3.oas.models.examples.Example openingExample =
                nn(body.getExamples().get("opening"));
        final Map<String, Object> opening =
                Json.mapper().convertValue(openingExample.getValue(), Map.class);
        final Map<String, Object> closing = Json.mapper().convertValue(
                nn(body.getExamples().get("closing")).getValue(), Map.class);
        assertAll(
                () -> assertEquals("OPEN-1", opening.get("code")),
                () -> assertEquals("CLOSE-9", closing.get("code")),
                () -> assertEquals("An opening balance", openingExample.getSummary()),
                () -> assertEquals("OPEN-", nn(nn(nn(api.getPaths().get("/ledgers/stream")).getGet()
                        .getParameters().get(0).getExamples()).get("opening")).getValue()));
    }

    @Test
    @DisplayName("an explicit value wins over dataValue, and a non-JSON serializedValue is not parsed")
    void explicitValueWins() {
        final ObjectNode withValue = Json.mapper().createObjectNode().put("value", "kept");
        withValue.putObject("dataValue").put("code", "dropped");
        OpenApi32ParserExtension.rewriteExample(withValue, true);
        final ObjectNode text = Json.mapper().createObjectNode().put("serializedValue", "a=b");
        OpenApi32ParserExtension.rewriteExample(text, false);
        assertAll(
                () -> assertEquals("kept", withValue.get("value").asText()),
                () -> assertFalse(withValue.has("dataValue")),
                () -> assertFalse(text.has("value")),
                () -> assertFalse(text.has("serializedValue")));
    }

    @Test
    @DisplayName("the 3.2 fields the model cannot hold produce no parser message")
    void unmodelledFieldsAreDroppedQuietly() throws URISyntaxException {
        final SwaggerParseResult result =
                new OpenAPIParser().readLocation(fixturePath(), null, new ParseOptions());
        assertTrue(result.getMessages() == null || result.getMessages().isEmpty(),
                () -> "unexpected messages: " + result.getMessages());
    }

    @Test
    @DisplayName("server names and the nested tag fields reach the model through their carriers")
    void unmodelledFieldsAreCarried() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final io.swagger.v3.oas.models.tags.Tag audits = api.getTags().get(1);
        assertAll(
                () -> assertEquals("main-region", api.getServers().get(0).getExtensions()
                        .get(OpenApi32ParserExtension.SERVER_NAME)),
                () -> assertEquals("accounts", audits.getExtensions().get(OpenApi32ParserExtension.TAG_PARENT)),
                () -> assertEquals("nav", audits.getExtensions().get(OpenApi32ParserExtension.TAG_KIND)),
                () -> assertEquals("Account audits",
                        audits.getExtensions().get(OpenApi32ParserExtension.TAG_SUMMARY)));
    }

    @Test
    @DisplayName("a referenced parameter keeps the description written beside its reference")
    void parameterReferenceIsParsed() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final Parameter parameter = nn(api.getPaths().get("/ledgers/{code}")).getParameters().get(0);
        assertAll(
                () -> assertEquals("#/components/parameters/LedgerCode", parameter.get$ref()),
                () -> assertEquals("The code of the ledger book to read", parameter.getDescription()));
    }

    @TestFactory
    @DisplayName("every SDK generates from the 3.2 fixture")
    Stream<DynamicTest> everySdkGeneratesFromTheFixture() {
        return ProbeGenerator.ALL_GENERATORS.stream().map(generator -> DynamicTest.dynamicTest(
                generator, () -> {
                    final String all = generate(generator);
                    assertAll(
                            () -> assertTrue(all.contains("balance"), "the $self-resolved schema is missing"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("auditledger"), "the operation is missing"),
                            () -> assertTrue(all.contains("main-region") && all.contains("spare-region"),
                                    "the server names are missing"),
                            () -> assertTrue(all.contains("One ledger book"),
                                    "the path item summary is missing"),
                            () -> assertTrue(all.contains("The code of the ledger book to read"),
                                    "the reference description override is missing"),
                            () -> assertTrue(all.contains("Account audits"),
                                    "the tag summary is missing"),
                            () -> assertTrue(all.contains("It belongs to the accounts API group."),
                                    "the tag parent is missing"));
                }));
    }

    /** Generates one SDK from the fixture and returns the text of every file it wrote. */
    static String generate(final String generator) throws Exception {
        final Path out = Path.of("target", "oas32", generator).toAbsolutePath();
        ProbeGenerator.generate(generator, Map.of("skipFormatter", "true"), out, fixturePath());
        try (Stream<Path> files = Files.walk(out)) {
            return files.filter(Files::isRegularFile)
                    .map(OpenApi32ParserExtensionTest::readQuietly)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String readQuietly(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
