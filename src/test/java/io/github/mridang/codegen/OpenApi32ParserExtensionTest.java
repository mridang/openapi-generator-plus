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
                () -> assertEquals(6, api.getPaths().keySet().stream()
                        .map(OpenApi32ParserExtension::resourcePath).distinct().count()));
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

    @Test
    @DisplayName("a QUERY operation and a custom method get path items of their own after their path")
    void extraMethodsAreRelocated() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final List<String> keys = List.copyOf(api.getPaths().keySet());
        final int search = keys.indexOf("/ledgers/search");
        final io.swagger.v3.oas.models.Operation query =
                nn(api.getPaths().get("/ledgers/search QUERY")).getPost();
        final io.swagger.v3.oas.models.Operation link =
                nn(api.getPaths().get("/ledgers/search LINK")).getPost();
        assertAll(
                () -> assertEquals(List.of("/ledgers/search QUERY", "/ledgers/search LINK"),
                        keys.subList(search + 1, search + 3)),
                () -> assertEquals("QUERY", OpenApi32ParserExtension.declaredMethod(query)),
                () -> assertNotNull(query.getRequestBody(), "the QUERY body was dropped"),
                () -> assertEquals("LINK", OpenApi32ParserExtension.declaredMethod(link)),
                () -> assertEquals("Ledger search",
                        nn(api.getPaths().get("/ledgers/search QUERY")).getSummary()),
                () -> assertEquals("/ledgers/search",
                        OpenApi32ParserExtension.resourcePath("/ledgers/search QUERY")),
                () -> assertNull(OpenApi32ParserExtension.declaredMethod(
                        nn(api.getPaths().get("/ledgers")).getPost())));
    }

    @Test
    @DisplayName("a fixed method or an unsendable name in additionalOperations is left out and reported")
    void badAdditionalOperationsAreReported(@TempDir final Path dir) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, "openapi: 3.2.0\n"
                + "info: {title: T, version: '1'}\n"
                + "paths:\n"
                + "  /items:\n"
                + "    additionalOperations:\n"
                + "      POST: {responses: {'204': {description: OK}}}\n"
                + "      purge: {responses: {'204': {description: OK}}}\n"
                + "      M-SEARCH: {responses: {'204': {description: OK}}}\n"
                + "      PURGE: {responses: {'204': {description: OK}}}\n"
                + "  /things:\n"
                + "    $ref: '#/components/pathItems/Things'\n"
                + "components:\n"
                + "  pathItems:\n"
                + "    Things:\n"
                + "      query: {responses: {'200': {description: OK}}}\n", StandardCharsets.UTF_8);
        final OpenAPI api = parse(spec.toString());
        final List<String> warnings = SpecWarnings.collect(api).stream()
                .map(SpecWarnings.Warning::toString)
                .filter(warning -> warning.contains("additionalOperations"))
                .collect(Collectors.toList());
        assertAll(
                () -> assertEquals(List.of("/items", "/items PURGE", "/things", "/things QUERY"),
                        List.copyOf(api.getPaths().keySet())),
                () -> assertEquals(List.of(
                        "#/paths/~1items/additionalOperations/POST: the method 'POST' has its own"
                                + " field in the path item and must not be in additionalOperations;"
                                + " the operation is ignored",
                        "#/paths/~1items/additionalOperations/purge: the method 'purge' is not"
                                + " supported: a custom method must be upper-case letters, digits"
                                + " and underscores, starting with a letter; the operation is ignored",
                        "#/paths/~1items/additionalOperations/M-SEARCH: the method 'M-SEARCH' is not"
                                + " supported: a custom method must be upper-case letters, digits"
                                + " and underscores, starting with a letter; the operation is ignored"),
                        warnings));
    }

    @Test
    @DisplayName("a form-urlencoded querystring parameter becomes an exploded form query parameter")
    void queryStringBecomesAnExplodedFormParameter() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final Parameter filter =
                nn(api.getPaths().get("/ledgers/filter")).getGet().getParameters().get(0);
        assertAll(
                () -> assertEquals("query", filter.getIn()),
                () -> assertEquals(Parameter.StyleEnum.FORM, filter.getStyle()),
                () -> assertEquals(true, filter.getExplode()),
                () -> assertNull(filter.getContent()),
                () -> assertTrue(filter.getRequired()),
                () -> assertEquals("#/components/schemas/LedgerFilter", nn(filter.getSchema()).get$ref(),
                        "the content schema was not moved to the parameter"));
    }

    @Test
    @DisplayName("a querystring parameter of another media type is left out, and mixing is reported")
    void unsupportedQueryStringsAreReported(@TempDir final Path dir) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, "openapi: 3.2.0\n"
                + "info: {title: T, version: '1'}\n"
                + "paths:\n"
                + "  /items:\n"
                + "    parameters:\n"
                + "      - {name: limit, in: query, schema: {type: integer}}\n"
                + "    get:\n"
                + "      parameters:\n"
                + "        - $ref: '#/components/parameters/Filter'\n"
                + "      responses: {'204': {description: OK}}\n"
                + "    put:\n"
                + "      parameters:\n"
                + "        - $ref: '#/components/parameters/Path'\n"
                + "        - name: raw\n"
                + "          in: querystring\n"
                + "          content: {text/plain: {schema: {type: string}}}\n"
                + "      responses: {'204': {description: OK}}\n"
                + "components:\n"
                + "  parameters:\n"
                + "    Filter:\n"
                + "      name: filter\n"
                + "      in: querystring\n"
                + "      content:\n"
                + "        application/x-www-form-urlencoded:\n"
                + "          schema: {type: object, properties: {q: {type: string}}}\n"
                + "    Path:\n"
                + "      name: path\n"
                + "      in: querystring\n"
                + "      content: {application/jsonpath: {schema: {type: string}}}\n",
                StandardCharsets.UTF_8);
        final OpenAPI api = parse(spec.toString());
        final List<String> warnings = SpecWarnings.collect(api).stream()
                .map(SpecWarnings.Warning::toString).collect(Collectors.toList());
        assertAll(
                () -> assertEquals(List.of(
                        "#/components/parameters/Path: a querystring parameter is supported only"
                                + " with application/x-www-form-urlencoded content; the parameter"
                                + " is ignored",
                        "#/paths/~1items/get: a querystring parameter must not be used together"
                                + " with query parameters; both are sent",
                        "#/paths/~1items/put/parameters/1: a querystring parameter is supported"
                                + " only with application/x-www-form-urlencoded content; the"
                                + " parameter is ignored"),
                        warnings),
                () -> assertEquals(List.of(), nn(api.getPaths().get("/items")).getPut().getParameters()),
                () -> assertFalse(api.getComponents().getParameters().containsKey("Path")));
    }

    @Test
    @DisplayName("style: cookie reaches the model through its carrier and the parameter keeps form")
    void cookieStyleIsCarried() throws URISyntaxException {
        final OpenAPI api = parse(fixturePath());
        final Parameter region =
                nn(api.getPaths().get("/ledgers/files/{filePath}")).getGet().getParameters().get(1);
        assertAll(
                () -> assertEquals("cookie", region.getIn()),
                () -> assertEquals(Parameter.StyleEnum.FORM, region.getStyle()),
                () -> assertEquals(false, region.getExplode()),
                () -> assertEquals(OpenApi32ParserExtension.COOKIE_STYLE,
                        region.getExtensions().get(OpenApi32ParserExtension.PARAMETER_STYLE)));
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
                                    "the tag parent is missing"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("searchledgers") && all.contains("QUERY"),
                                    "the QUERY operation is missing"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("linkledgers") && all.contains("LINK"),
                                    "the LINK operation is missing"),
                            () -> assertFalse(all.contains("search QUERY") || all.contains("search LINK"),
                                    "the internal path-item key leaked into the SDK"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("filterledgers") && all.contains("LedgerFilter"),
                                    "the querystring operation or its model is missing"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("cookiepairs"),
                                    "the style: cookie parameter is not written as cookie pairs"),
                            () -> assertTrue(all.toLowerCase(java.util.Locale.ROOT).replace("_", "")
                                    .contains("allowreservedinpath"),
                                    "the allowReserved path parameter keeps no reserved character"),
                            () -> assertTrue(all.contains("books/2024.csv"),
                                    "the single parameter example is not documented"),
                            () -> assertTrue(all.contains("OPEN-1") && all.contains("An opening balance"),
                                    "the request body example is not documented"),
                            () -> assertTrue(all.contains("https://example.com/ledgers/archived.json"),
                                    "the external example is not documented"),
                            () -> assertTrue(all.contains("x-audience") && all.contains("ledger-team"),
                                    "the exposed extension is not documented"));
                }));
    }

    /** Generates one SDK from the fixture and returns the text of every file it wrote. */
    static String generate(final String generator) throws Exception {
        final Path out = Path.of("target", "oas32", generator).toAbsolutePath();
        ProbeGenerator.generate(
                generator, Map.of("skipFormatter", "true", "exposedExtensions", "x-audience"), out,
                fixturePath());
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
