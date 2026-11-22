package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import io.github.mridang.codegen.generators.java.BetterJavaCodegen;

/**
 * Validates that the operation/parameter/property decorator pass installed in
 * {@link AbstractBetterCodegen} populates the expected
 * {@code vendorExtensions[op|param|prop].*} sub-maps. The test exercises the
 * three {@code populate*Decorators} methods directly against synthetic
 * fixtures so it stays independent of any specific spec or language
 * generator. The {@code BetterJavaCodegen} subclass is used as a concrete
 * instantiation of the abstract base.
 */
class OperationDecoratorTest {

    /**
     * Catalog of operation-level decorator keys this test guards. Adding a
     * new property to {@link AbstractBetterCodegen#populateOperationDecorators}
     * means adding it here too.
     */
    private static final List<String> OP_KEYS = List.of(
            "effectiveConsumes",
            "errorResponses",
            "hasErrorResponses",
            "errorSchemaResponses",
            "hasErrorSchemaResponses",
            "effectiveProduces",
            "optionsClassName",
            "serverClassName",
            "apiClassName",
            "optionsParamRequired",
            "hasOptionsParam",
            "optionsCallSettingsOnly",
            "optionsMinted",
            "optionsForEveryOperation",
            "hasPerOperationServer",
            "requestBodyKind",
            "returnKind",
            "hasReturnType",
            "hasQueryParams",
            "hasHeaderParams",
            "hasFormParams",
            "hasCookieParams",
            "hasPathParams",
            "hasBodyParam",
            "streamKind",
            "hasStreamMethod",
            "streamOnly",
            "streamItemType",
            "streamEventIsText",
            "hasStreamRequestBody",
            "streamMethodName",
            "hasPagesMethod",
            "pagesMethodName");

    /**
     * Catalog of parameter-level decorator keys this test guards.
     */
    private static final List<String> PARAM_KEYS = List.of(
            "pluralExamples",
            "hasPluralExamples",
            "serializationMode",
            "pathSerialisationKind",
            "requiresPathEncoding",
            "isScalar");

    /**
     * Catalog of property-level decorator keys this test guards.
     */
    private static final List<String> PROP_KEYS = List.of(
            "pluralExamples",
            "hasPluralExamples",
            "isScalar",
            "isContainerOfModel");

    @Test
    @SuppressWarnings("unchecked")
    void operationDecoratorKeysAreAttachedAndWellFormed() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();

        final CodegenOperation op = new CodegenOperation();
        op.operationId = "getPetById";
        op.consumes = List.of(Map.of("mediaType", "application/json"));
        op.produces = List.of(Map.of("mediaType", "application/xml"));
        op.returnType = "Pet";
        op.returnBaseType = "Pet";
        op.allParams = new ArrayList<>();
        op.queryParams = new ArrayList<>();
        op.headerParams = new ArrayList<>();
        op.formParams = new ArrayList<>();
        op.cookieParams = new ArrayList<>();
        op.pathParams = new ArrayList<>();
        op.bodyParams = new ArrayList<>();
        op.optionalParams = new ArrayList<>();
        op.requiredParams = new ArrayList<>();

        codegen.populateOperationDecorators(op);

        assertNotNull(op.vendorExtensions, "vendorExtensions allocated on demand");
        final Map<String, Object> deco = (Map<String, Object>) op.vendorExtensions.get("op");
        assertNotNull(deco, "op decorator sub-map present under vendorExtensions");

        for (final String k : OP_KEYS) {
            assertTrue(deco.containsKey(k), "op decorator key present: " + k);
        }

        assertEquals("application/json", deco.get("effectiveConsumes"));
        assertEquals("application/xml", deco.get("effectiveProduces"));
        assertEquals("GetPetByIdOptions", deco.get("optionsClassName"));
        assertEquals("GetPetByIdServer", deco.get("serverClassName"));
        assertEquals("GetPetByIdApi", deco.get("apiClassName"));
        assertEquals(false, deco.get("optionsParamRequired"));
        assertEquals(false, deco.get("hasOptionsParam"));
        assertEquals(false, deco.get("hasPerOperationServer"));
        assertEquals("none", deco.get("requestBodyKind"));
        assertEquals("model", deco.get("returnKind"));
        assertEquals(true, deco.get("hasReturnType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void declaredErrorResponsesBecomeRowsAndTheSpecLevelSchemaList() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        op.path = "/things/{id}";
        op.responses = new ArrayList<>();
        op.responses.add(response("200", "Thing", true));
        op.responses.add(response("404", "ThingMissing", true));
        op.responses.add(response("0", "Fallback", true));
        op.responses.add(response("5XX", "plain", false));
        codegen.populateOperationDecorators(op);

        final Map<String, Object> deco = decorator(op);
        final List<Map<String, Object>> rows =
                (List<Map<String, Object>>) Objects.requireNonNull(deco.get("errorResponses"));
        assertEquals(List.of("404", "5XX", "default"), rows.stream().map(r -> r.get("status")).collect(java.util.stream.Collectors.toList()));
        assertEquals("NotFoundException", rows.get(0).get("statusClassName"));
        assertEquals("ThingMissingException", rows.get(0).get("schemaClassName"));
        assertEquals(false, rows.get(1).get("hasSchema"));
        assertEquals(true, deco.get("hasErrorResponses"));
        assertEquals(2, ((List<?>) Objects.requireNonNull(deco.get("errorSchemaResponses"))).size());
        final List<Map<String, Object>> schemas =
                (List<Map<String, Object>>) Objects.requireNonNull(codegen.additionalProperties().get("errorSchemas"));
        assertEquals(List.of("ThingMissing", "Fallback"), schemas.stream().map(s -> s.get("schemaName")).collect(java.util.stream.Collectors.toList()));
        assertEquals("ApiException", schemas.get(1).get("parentClassName"));
        assertEquals("thing_missing_exception", schemas.get(0).get("classFilename"));
        assertEquals("not_found_exception", schemas.get(0).get("parentFilename"));
        assertEquals(false, schemas.get(0).get("parentIsApi"));
        assertEquals(true, schemas.get(1).get("parentIsApi"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorSuffixFollowsTheLanguageConvention() {
        assertEquals("Exception", ((AbstractBetterCodegen) new BetterJavaCodegen()).errorSuffix());
        assertEquals("Error", ((AbstractBetterCodegen) new io.github.mridang.codegen.generators.ruby.BetterRubyCodegen()).errorSuffix());
    }

    @Test
    void csharpNamesTheNotImplementedStatusClassAwayFromTheSystemOne() {
        final AbstractBetterCodegen csharp = new io.github.mridang.codegen.generators.csharp.BetterCSharpCodegen();
        assertEquals("NotImplementedStatusException", csharp.statusClassName("NotImplemented"));
        assertEquals("NotFoundException", csharp.statusClassName("NotFound"));
        assertEquals("NotImplementedException", ((AbstractBetterCodegen) new BetterJavaCodegen()).statusClassName("NotImplemented"));
    }

    @Test
    void streamingSignalsSelectTheStreamKindAndStreamOnly() {
        assertStream("text/event-stream", "Event", false, "sse", true, "Event", false);
        assertStream("text/event-stream", "String", true, "sse", true, "String", true);
        assertStream("application/x-ndjson", "Row", false, "jsonlines", true, "Row", false);
        assertStream("application/jsonl", "Row", false, "jsonlines", true, "Row", false);
        assertStream("application/jsonlines", "Row", false, "jsonlines", true, "Row", false);
        assertStream("application/octet-stream", "File", false, "binary", false, null, false);
        assertStream("application/json", "Row", false, "none", false, null, false);
    }

    @Test
    void anOperationMixingAStreamingAndAJsonSuccessTypeGetsNoStreamMethod() {
        final CodegenOperation op = bareOperation();
        final org.openapitools.codegen.CodegenResponse r = new org.openapitools.codegen.CodegenResponse();
        r.code = "200";
        final java.util.LinkedHashMap<String, org.openapitools.codegen.CodegenMediaType> content =
                new java.util.LinkedHashMap<>();
        content.put("text/event-stream", media("Event", false));
        content.put("application/json", media("Event", false));
        r.setContent(content);
        op.responses = new ArrayList<>(List.of(r));
        ((AbstractBetterCodegen) new BetterJavaCodegen()).populateOperationDecorators(op);
        assertEquals("none", decorator(op).get("streamKind"));
        assertEquals(false, decorator(op).get("streamOnly"));
    }

    @Test
    void aRawBinaryRequestBodyGivesTheOperationAStreamMethodAndTheNameFollowsTheCasing() {
        final CodegenOperation op = bareOperation();
        op.nickname = "replaceBlob";
        op.bodyParam = new CodegenParameter();
        op.bodyParam.isBinary = true;
        ((AbstractBetterCodegen) new BetterJavaCodegen()).populateOperationDecorators(op);
        assertEquals(true, decorator(op).get("hasStreamRequestBody"));
        assertEquals(true, decorator(op).get("hasStreamMethod"));
        assertEquals("none", decorator(op).get("streamKind"));
        assertEquals("replaceBlobStream", decorator(op).get("streamMethodName"));
        op.nickname = "replace_blob";
        ((AbstractBetterCodegen) new BetterJavaCodegen()).populateOperationDecorators(op);
        assertEquals("replace_blob_stream", decorator(op).get("streamMethodName"));
    }

    // ---- Pagination (#163, D123, D124) ------------------------------------------------------

    private static CodegenParameter queryParam(String name, boolean required, boolean integer) {
        final CodegenParameter p = new CodegenParameter();
        p.baseName = name;
        p.paramName = name.replaceAll("_([a-z])", "$1");
        p.required = required;
        p.isString = !integer;
        p.isInteger = integer;
        p.dataType = integer ? "Integer" : "String";
        return p;
    }

    private static CodegenProperty prop(String name, boolean array, boolean string) {
        final CodegenProperty p = new CodegenProperty();
        p.baseName = name;
        p.name = name;
        p.isArray = array;
        p.isString = string;
        if (array) {
            p.items = new CodegenProperty();
            p.items.dataType = "Item";
        }
        return p;
    }

    private static org.openapitools.codegen.CodegenResponse jsonResponse(String code, CodegenProperty schema) {
        final org.openapitools.codegen.CodegenResponse r = new org.openapitools.codegen.CodegenResponse();
        r.code = code;
        final java.util.LinkedHashMap<String, org.openapitools.codegen.CodegenMediaType> content =
                new java.util.LinkedHashMap<>();
        content.put("application/json", new org.openapitools.codegen.CodegenMediaType(
                schema, new java.util.LinkedHashMap<>(), new java.util.HashMap<>()));
        r.setContent(content);
        return r;
    }

    private static CodegenProperty page(CodegenProperty... vars) {
        final CodegenProperty p = new CodegenProperty();
        p.dataType = "ItemPage";
        p.vars = new ArrayList<>(List.of(vars));
        return p;
    }

    /** A GET list with an optional limit and cursor and a page of items and a next cursor. */
    private static CodegenOperation tokenOperation() {
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        op.queryParams = new ArrayList<>(List.of(queryParam("limit", false, true), queryParam("cursor", false, false)));
        op.responses = new ArrayList<>(List.of(jsonResponse("200",
                page(prop("items", true, false), prop("next_cursor", false, true)))));
        return op;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pagination(CodegenOperation op) {
        return Objects.requireNonNull((Map<String, Object>) decorator(op).get("pagination"));
    }

    private static CodegenOperation populated(CodegenOperation op) {
        return populated(new BetterJavaCodegen(), op);
    }

    private static CodegenOperation populated(AbstractBetterCodegen codegen, CodegenOperation op) {
        codegen.populateOperationDecorators(op);
        return op;
    }

    @Test
    void aTokenCursorListGetsAPagedMethodThatReadsTheNamesOfItsStyle() {
        final CodegenOperation op = populated(tokenOperation());
        assertEquals(true, decorator(op).get("hasPagesMethod"));
        assertEquals("listItemsPages", decorator(op).get("pagesMethodName"));
        final Map<String, Object> p = pagination(op);
        assertEquals("token", p.get("style"));
        assertEquals("inference", p.get("source"));
        assertEquals("cursor", p.get("requestParam"));
        assertEquals("String", p.get("cursorType"));
        assertEquals("next_cursor", p.get("responseProperty"));
        assertEquals("items", p.get("itemsProperty"));
        assertEquals("Item", p.get("itemType"));
        assertEquals(false, p.get("isLink"));
    }

    @Test
    void everyTokenNameOfTheDesignTableIsRecognisedWithoutCaseOrSeparators() {
        for (final String request : List.of("cursor", "after", "page_token", "next-token", "continuationToken", "marker")) {
            for (final String response : List.of("next_cursor", "nextPageToken", "next_token", "next-marker",
                    "NextContinuationToken", "continuation_token", "next")) {
                final CodegenOperation op = tokenOperation();
                op.queryParams = new ArrayList<>(List.of(queryParam(request, false, false)));
                op.responses = new ArrayList<>(List.of(jsonResponse("200",
                        page(prop("items", true, false), prop(response, false, true)))));
                populated(op);
                assertEquals(true, decorator(op).get("hasPagesMethod"), request + " / " + response);
            }
        }
    }

    @Test
    void theNameOfThePagedMethodFollowsTheCasing() {
        final CodegenOperation op = tokenOperation();
        op.nickname = "list_items";
        populated(op);
        assertEquals("list_items_pages", decorator(op).get("pagesMethodName"));
    }

    @Test
    void aDeclaredLinkHeaderOnAnArrayBodyGetsAPagedMethod() {
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        final CodegenProperty body = prop("body", true, false);
        final org.openapitools.codegen.CodegenResponse r = jsonResponse("200", body);
        r.headers.add(prop("Link", false, true));
        op.responses = new ArrayList<>(List.of(r));
        populated(op);
        assertEquals(true, decorator(op).get("hasPagesMethod"));
        final Map<String, Object> p = pagination(op);
        assertEquals("link", p.get("style"));
        assertEquals(true, p.get("isLink"));
        assertEquals(true, p.get("itemsFromBody"));
        assertEquals("Item", p.get("itemType"));
    }

    @Test
    void aLinkHeaderOnAnObjectBodyReadsTheOneArrayProperty() {
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        final org.openapitools.codegen.CodegenResponse r =
                jsonResponse("200", page(prop("rows", true, false), prop("total", false, false)));
        r.headers.add(prop("link", false, true));
        op.responses = new ArrayList<>(List.of(r));
        populated(op);
        assertEquals("rows", pagination(op).get("itemsProperty"));
        assertEquals(false, pagination(op).get("itemsFromBody"));
    }

    @Test
    void anOperationThatIsNotEligibleGetsNoPagedMethod() {
        // two array properties make the item list ambiguous
        CodegenOperation op = tokenOperation();
        op.responses = new ArrayList<>(List.of(jsonResponse("200",
                page(prop("items", true, false), prop("owners", true, false), prop("next_cursor", false, true)))));
        assertNotPaged(op);
        // two cursor parameter candidates
        op = tokenOperation();
        op.queryParams.add(queryParam("marker", false, false));
        assertNotPaged(op);
        // two next-cursor candidates
        op = tokenOperation();
        op.responses = new ArrayList<>(List.of(jsonResponse("200",
                page(prop("items", true, false), prop("next_cursor", false, true), prop("next", false, true)))));
        assertNotPaged(op);
        // not a GET
        op = tokenOperation();
        op.httpMethod = "POST";
        assertNotPaged(op);
        // two 2xx responses
        op = tokenOperation();
        op.responses.add(jsonResponse("201", page(prop("items", true, false))));
        assertNotPaged(op);
        // a required cursor has no first-request value
        op = tokenOperation();
        op.queryParams.get(1).required = true;
        assertNotPaged(op);
        // a cursor with no next-cursor property, and the other way round
        op = tokenOperation();
        op.responses = new ArrayList<>(List.of(jsonResponse("200", page(prop("items", true, false)))));
        assertNotPaged(op);
        op = tokenOperation();
        op.queryParams = new ArrayList<>(List.of(queryParam("limit", false, true)));
        assertNotPaged(op);
        // a token cursor needs an object, not an array body
        op = tokenOperation();
        op.responses = new ArrayList<>(List.of(jsonResponse("200", prop("body", true, false))));
        assertNotPaged(op);
        // a token cursor and a Link header are two styles
        op = tokenOperation();
        op.responses.get(0).headers.add(prop("Link", false, true));
        assertNotPaged(op);
        // a cursor of another type than the response property cannot be followed
        op = tokenOperation();
        op.queryParams.set(1, queryParam("cursor", false, true));
        assertNotPaged(op);
        // an operation with a server of its own
        op = tokenOperation();
        op.servers = new ArrayList<>(List.of(new org.openapitools.codegen.CodegenServer()));
        assertNotPaged(op);
        // an operation that is streamed
        op = tokenOperation();
        final org.openapitools.codegen.CodegenResponse sse = new org.openapitools.codegen.CodegenResponse();
        sse.code = "200";
        final java.util.LinkedHashMap<String, org.openapitools.codegen.CodegenMediaType> content = new java.util.LinkedHashMap<>();
        content.put("text/event-stream", media("Event", false));
        sse.setContent(content);
        op.responses = new ArrayList<>(List.of(sse));
        assertNotPaged(op);
    }

    @Test
    void anOperationWithNoPaginationSignalGetsNoPagedMethod() {
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        op.responses = new ArrayList<>(List.of(jsonResponse("200", page(prop("items", true, false)))));
        assertNotPaged(op);
    }

    private static void assertNotPaged(CodegenOperation op) {
        populated(op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        assertEquals(null, decorator(op).get("pagination"));
    }

    private static BetterJavaCodegen withPaginationOptions(@javax.annotation.Nullable String mode, @javax.annotation.Nullable Object overrides) {
        final BetterJavaCodegen codegen = new BetterJavaCodegen();
        if (mode != null) {
            codegen.additionalProperties().put("pagination", mode);
        }
        if (overrides != null) {
            codegen.additionalProperties().put("paginationOverrides", overrides);
        }
        codegen.processOpts();
        return codegen;
    }

    @Test
    void theOverrideOptionPagesAnOperationThatInferenceLeavesAlone() {
        final String json = "{\"listItems\":{\"style\":\"token\",\"requestParam\":\"pos\","
                + "\"responseProperty\":\"following\",\"itemsProperty\":\"rows\"}}";
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        op.queryParams = new ArrayList<>(List.of(queryParam("pos", false, false)));
        op.responses = new ArrayList<>(List.of(jsonResponse("200",
                page(prop("rows", true, false), prop("other", true, false), prop("following", false, true)))));
        populated(new BetterJavaCodegen(), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        populated(withPaginationOptions(null, json), op);
        assertEquals(true, decorator(op).get("hasPagesMethod"));
        assertEquals("override", pagination(op).get("source"));
        assertEquals("pos", pagination(op).get("requestParam"));
        assertEquals("rows", pagination(op).get("itemsProperty"));
    }

    @Test
    void theOverrideOptionAcceptsAMapAndNoneSwitchesOneOperationOff() {
        final CodegenOperation op = populated(tokenOperation());
        assertEquals(true, decorator(op).get("hasPagesMethod"));
        populated(withPaginationOptions(null, Map.of("listItems", "none")), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        assertEquals(null, decorator(op).get("pagination"));
        final CodegenOperation other = tokenOperation();
        other.operationId = "listOthers";
        populated(withPaginationOptions(null, Map.of("listItems", "none")), other);
        assertEquals(true, decorator(other).get("hasPagesMethod"), "another operation is not switched off");
    }

    @Test
    void anOverrideThatCannotBeFollowedLeavesTheOperationManual() {
        final CodegenOperation op = tokenOperation();
        populated(withPaginationOptions(null, Map.of("listItems", Map.of("style", "offset"))), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        populated(withPaginationOptions(null, Map.of("listItems", Map.of("style", "token",
                "requestParam", "missing", "responseProperty", "next_cursor"))), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
    }

    @Test
    void aMalformedOverrideOptionIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> withPaginationOptions(null, "not json"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> withPaginationOptions("sometimes", null));
    }

    @Test
    void paginationOffSwitchesTheWholeFeatureOff() {
        final CodegenOperation op = tokenOperation();
        populated(withPaginationOptions("off", null), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        assertEquals(null, decorator(op).get("pagination"));
    }

    private static CodegenOperation hintedOperation() {
        final CodegenOperation op = bareOperation();
        op.httpMethod = "GET";
        op.queryParams = new ArrayList<>(List.of(queryParam("pos", false, false)));
        op.responses = new ArrayList<>(List.of(jsonResponse("200",
                page(prop("rows", true, false), prop("following", false, true)))));
        op.vendorExtensions = new HashMap<>();
        return op;
    }

    @Test
    void aFernHintPagesAnOperationAndTheOverrideBeatsIt() {
        final CodegenOperation op = hintedOperation();
        op.vendorExtensions.put("x-fern-pagination", Map.of(
                "cursor", "$request.pos", "next_cursor", "$response.following", "results", "$response.rows"));
        populated(op);
        assertEquals(true, decorator(op).get("hasPagesMethod"));
        assertEquals("hint", pagination(op).get("source"));
        assertEquals("pos", pagination(op).get("requestParam"));
        assertEquals("following", pagination(op).get("responseProperty"));
        assertEquals("rows", pagination(op).get("itemsProperty"));
        populated(withPaginationOptions(null, Map.of("listItems", "none")), op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
        populated(withPaginationOptions(null, Map.of("listItems", Map.of("style", "token",
                "requestParam", "pos", "responseProperty", "following", "itemsProperty", "rows"))), op);
        assertEquals("override", pagination(op).get("source"));
    }

    @Test
    void aSpeakeasyCursorHintPagesAnOperationAndAHintOfAnotherStyleIsIgnored() {
        final CodegenOperation op = hintedOperation();
        op.vendorExtensions.put("x-speakeasy-pagination", Map.of(
                "type", "cursor",
                "inputs", List.of(Map.of("name", "pos", "in", "parameters", "type", "cursor")),
                "outputs", Map.of("nextCursor", "$.following", "results", "$.rows")));
        populated(op);
        assertEquals("hint", pagination(op).get("source"));
        assertEquals("pos", pagination(op).get("requestParam"));
        final CodegenOperation offset = hintedOperation();
        offset.vendorExtensions.put("x-speakeasy-pagination", Map.of("type", "offsetLimit"));
        populated(offset);
        assertEquals(false, decorator(offset).get("hasPagesMethod"));
    }

    @Test
    void aHintThatNamesAMissingParameterLeavesTheOperationManualWithoutFallingBackToInference() {
        final CodegenOperation op = tokenOperation();
        op.vendorExtensions = new HashMap<>();
        op.vendorExtensions.put("x-fern-pagination", Map.of(
                "cursor", "$request.nope", "next_cursor", "$response.next_cursor"));
        populated(op);
        assertEquals(false, decorator(op).get("hasPagesMethod"));
    }

    private static org.openapitools.codegen.CodegenMediaType media(String type, boolean isString) {
        final CodegenProperty p = new CodegenProperty();
        p.dataType = type;
        p.isString = isString;
        p.isBinary = "File".equals(type);
        return new org.openapitools.codegen.CodegenMediaType(p, new java.util.LinkedHashMap<>(), new java.util.HashMap<>());
    }

    private static void assertStream(String mediaType, String type, boolean isString,
            String kind, boolean only, @javax.annotation.Nullable String item, boolean text) {
        final CodegenOperation op = bareOperation();
        final org.openapitools.codegen.CodegenResponse r = new org.openapitools.codegen.CodegenResponse();
        r.code = "200";
        final java.util.LinkedHashMap<String, org.openapitools.codegen.CodegenMediaType> content =
                new java.util.LinkedHashMap<>();
        content.put(mediaType, media(type, isString));
        r.setContent(content);
        op.responses = new ArrayList<>(List.of(r));
        ((AbstractBetterCodegen) new BetterJavaCodegen()).populateOperationDecorators(op);
        final Map<String, Object> deco = decorator(op);
        assertEquals(kind, deco.get("streamKind"), mediaType);
        assertEquals(only, deco.get("streamOnly"), mediaType);
        assertEquals(item, deco.get("streamItemType"), mediaType);
        assertEquals(text, deco.get("streamEventIsText"), mediaType);
        assertEquals(!"none".equals(kind), deco.get("hasStreamMethod"), mediaType);
    }

    private static org.openapitools.codegen.CodegenResponse response(String code, String dataType, boolean model) {
        final org.openapitools.codegen.CodegenResponse r = new org.openapitools.codegen.CodegenResponse();
        r.code = code;
        r.dataType = dataType;
        r.isModel = model;
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decorator(CodegenOperation op) {
        return Objects.requireNonNull((Map<String, Object>) op.vendorExtensions.get("op"));
    }

    private static CodegenOperation bareOperation() {
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "listItems";
        op.allParams = new ArrayList<>();
        op.queryParams = new ArrayList<>();
        op.headerParams = new ArrayList<>();
        op.formParams = new ArrayList<>();
        op.cookieParams = new ArrayList<>();
        op.pathParams = new ArrayList<>();
        op.bodyParams = new ArrayList<>();
        op.optionalParams = new ArrayList<>();
        op.requiredParams = new ArrayList<>();
        return op;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasOptionsSignatureEntry(CodegenOperation op) {
        final Map<String, Object> deco = decorator(op);
        for (final Map<String, Object> a :
                Objects.requireNonNull((List<Map<String, Object>>) deco.get("signatureArgs"))) {
            if (Boolean.TRUE.equals(a.get("isOptions"))) {
                return true;
            }
        }
        return false;
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationWithoutOptionsFieldsGetsNoOptionsObjectByDefault() {
        final CodegenOperation op = bareOperation();
        final AbstractBetterCodegen defaultHook =
                new BetterJavaCodegen() {
                    @Override
                    protected boolean mintsOptionsForEveryOperation() {
                        return false;
                    }
                };
        defaultHook.populateOperationDecorators(op);

        final Map<String, Object> deco = decorator(op);
        assertEquals(true, deco.get("optionsCallSettingsOnly"));
        assertEquals(false, deco.get("optionsMinted"));
        assertEquals(false, deco.get("optionsForEveryOperation"));
        assertEquals(false, hasOptionsSignatureEntry(op));
    }

    @Test
    @SuppressWarnings("unchecked")
    void optedInLanguageMintsAnOptionsObjectForEveryOperation() {
        final AbstractBetterCodegen codegen =
                new BetterJavaCodegen() {
                    @Override
                    protected boolean mintsOptionsForEveryOperation() {
                        return true;
                    }
                };
        final CodegenOperation op = bareOperation();
        codegen.populateOperationDecorators(op);

        final Map<String, Object> deco = decorator(op);
        assertEquals(true, deco.get("optionsCallSettingsOnly"));
        assertEquals(true, deco.get("optionsMinted"));
        assertEquals(true, deco.get("optionsForEveryOperation"));
        assertEquals(true, hasOptionsSignatureEntry(op));
        assertEquals(false, deco.get("optionsParamRequired"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationWithAuthIsNotCallSettingsOnly() {
        final CodegenOperation op = bareOperation();
        op.hasAuthMethods = true;
        ((AbstractBetterCodegen) new BetterJavaCodegen()).populateOperationDecorators(op);

        final Map<String, Object> deco = decorator(op);
        assertEquals(false, deco.get("optionsCallSettingsOnly"));
        assertEquals(true, hasOptionsSignatureEntry(op));
    }

    @Test
    @SuppressWarnings("unchecked")
    void effectiveMediaTypeDefaultsToJsonWhenAbsent() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();

        final CodegenOperation op = new CodegenOperation();
        op.operationId = "listPets";
        op.consumes = null;
        op.produces = null;
        op.returnType = null;
        op.allParams = new ArrayList<>();
        op.queryParams = new ArrayList<>();
        op.headerParams = new ArrayList<>();
        op.formParams = new ArrayList<>();
        op.cookieParams = new ArrayList<>();
        op.pathParams = new ArrayList<>();
        op.bodyParams = new ArrayList<>();
        op.optionalParams = new ArrayList<>();
        op.requiredParams = new ArrayList<>();

        codegen.populateOperationDecorators(op);
        final Map<String, Object> deco = Objects.requireNonNull(
                (Map<String, Object>) op.vendorExtensions.get("op"));
        assertEquals("application/json", deco.get("effectiveConsumes"));
        assertEquals("application/json", deco.get("effectiveProduces"));
        assertEquals("void", deco.get("returnKind"));
        assertEquals(false, deco.get("hasReturnType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parameterDecoratorKeysAreAttachedForPathScalar() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();

        final CodegenParameter p = new CodegenParameter();
        p.paramName = "petId";
        p.dataType = "Long";
        p.isPathParam = true;

        codegen.populateParameterDecorators(p);

        assertNotNull(p.vendorExtensions);
        final Map<String, Object> deco = (Map<String, Object>) p.vendorExtensions.get("param");
        assertNotNull(deco, "param decorator sub-map present");
        for (final String k : PARAM_KEYS) {
            assertTrue(deco.containsKey(k), "param decorator key present: " + k);
        }
        assertEquals("scalar", deco.get("pathSerialisationKind"));
        assertEquals(true, deco.get("requiresPathEncoding"));
        assertEquals(true, deco.get("isScalar"));
        assertEquals(false, deco.get("hasPluralExamples"));
        assertTrue(((List<?>) Objects.requireNonNull(deco.get("pluralExamples"))).isEmpty());
        assertEquals("default", deco.get("serializationMode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parameterDecoratorClassifiesDeepObjectQuery() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();

        final CodegenParameter p = new CodegenParameter();
        p.paramName = "filter";
        p.isQueryParam = true;
        p.isDeepObject = true;
        p.isModel = true;

        codegen.populateParameterDecorators(p);
        final Map<String, Object> deco = Objects.requireNonNull(
                (Map<String, Object>) p.vendorExtensions.get("param"));
        assertEquals("deepObject", deco.get("serializationMode"));
        assertEquals("none", deco.get("pathSerialisationKind"));
        assertEquals(false, deco.get("isScalar"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> queryDecorator(
            boolean required,
            boolean nullable,
            boolean allowEmpty,
            boolean deepObject,
            boolean content,
            boolean query) {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();
        final CodegenParameter p = new CodegenParameter();
        p.paramName = "note";
        p.isQueryParam = query;
        p.isHeaderParam = !query;
        p.required = required;
        p.isNullable = nullable;
        p.isAllowEmptyValue = allowEmpty;
        p.isDeepObject = deepObject;
        if (content) {
            final java.util.LinkedHashMap<String, org.openapitools.codegen.CodegenMediaType> media =
                    new java.util.LinkedHashMap<>();
            media.put("application/json", new org.openapitools.codegen.CodegenMediaType(null, null, null));
            p.setContent(media);
        }
        codegen.populateParameterDecorators(p);
        return Objects.requireNonNull((Map<String, Object>) p.vendorExtensions.get("param"));
    }

    @Test
    void requiredNullableQueryIsItsOwnKind() {
        final Map<String, Object> d = queryDecorator(true, true, false, false, false, true);
        assertEquals("requiredNullable", d.get("querySerializationKind"));
        assertEquals(true, d.get("queryRequiredNullable"));
        assertEquals(false, d.get("queryStyled"));
        assertEquals(false, d.get("queryStyledAllowEmpty"));
    }

    @Test
    void requiredNullableQueryWithAllowEmptyValueStaysRequiredNullable() {
        final Map<String, Object> d = queryDecorator(true, true, true, false, false, true);
        assertEquals("requiredNullable", d.get("querySerializationKind"));
        assertEquals(false, d.get("queryStyledAllowEmpty"));
    }

    @Test
    void optionalNullableAndRequiredNonNullableQueryStayStyled() {
        assertEquals(
                "styled",
                queryDecorator(false, true, false, false, false, true).get("querySerializationKind"));
        assertEquals(
                "styled",
                queryDecorator(true, false, false, false, false, true).get("querySerializationKind"));
        assertEquals(
                false,
                queryDecorator(false, true, false, false, false, true).get("queryRequiredNullable"));
    }

    @Test
    void requiredNullableDeepObjectAndContentKeepTheirKind() {
        assertEquals(
                "deepObject",
                queryDecorator(true, true, false, true, false, true).get("querySerializationKind"));
        assertEquals(
                "content",
                queryDecorator(true, true, false, false, true, true).get("querySerializationKind"));
    }

    @Test
    void requiredNullableHeaderIsNotAQueryKind() {
        final Map<String, Object> d = queryDecorator(true, true, false, false, false, false);
        assertEquals(false, d.get("queryRequiredNullable"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertyDecoratorKeysAreAttached() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();

        final CodegenProperty prop = new CodegenProperty();
        prop.baseName = "name";
        prop.dataType = "String";

        codegen.populatePropertyDecorators(prop, Collections.emptyList());

        assertNotNull(prop.vendorExtensions);
        final Map<String, Object> deco = (Map<String, Object>) prop.vendorExtensions.get("prop");
        assertNotNull(deco, "prop decorator sub-map present");
        for (final String k : PROP_KEYS) {
            assertTrue(deco.containsKey(k), "prop decorator key present: " + k);
        }
        assertEquals(true, deco.get("isScalar"));
        assertEquals(false, deco.get("isContainerOfModel"));
        assertEquals(false, deco.get("hasPluralExamples"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleSuccessOperationExposesNoSuccessVariants() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "getPetById";
        op.returnType = "Pet";

        codegen.populateOperationDecorators(op);

        final Map<String, Object> deco = (Map<String, Object>) Objects.requireNonNull(op.vendorExtensions.get("op"));
        assertEquals(false, deco.get("hasMultiple2xx"));
        assertEquals(Collections.emptyList(), deco.get("successResponses"));
        assertEquals(Collections.emptyList(), deco.get("successVariants"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleSuccessOperationExposesStatusToVariantMapping() {
        final AbstractBetterCodegen codegen = new BetterJavaCodegen();
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "reserveStock";
        op.returnType = "ReserveStockResponse";
        op.vendorExtensions.put(
                "x-success-responses",
                List.of(
                        Map.of("status", "200", "ref", "#/components/schemas/Reservation"),
                        Map.of("status", "201", "ref", "#/components/schemas/Receipt"),
                        Map.of("status", "202", "ref", "#/components/schemas/Reservation")));

        codegen.populateOperationDecorators(op);

        final Map<String, Object> deco = (Map<String, Object>) Objects.requireNonNull(op.vendorExtensions.get("op"));
        assertEquals(true, deco.get("hasMultiple2xx"));
        assertEquals("ReserveStockResponse", deco.get("successUnionType"));
        final List<Map<String, Object>> rows = (List<Map<String, Object>>) Objects.requireNonNull(deco.get("successResponses"));
        assertEquals(List.of("200", "201", "202"), rows.stream().map(r -> r.get("statusCode")).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of("Reservation", "Receipt", "Reservation"),
                rows.stream().map(r -> r.get("returnType")).collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of(0, 1, 0), rows.stream().map(r -> r.get("variantIndex")).collect(java.util.stream.Collectors.toList()));
        assertEquals(true, rows.get(0).get("isFirst"));
        assertEquals(true, rows.get(2).get("isLast"));
        final List<Map<String, Object>> variants = (List<Map<String, Object>>) Objects.requireNonNull(deco.get("successVariants"));
        assertEquals(List.of("200", "202"), variants.get(0).get("statusCodes"));
        assertEquals(List.of("201"), variants.get(1).get("statusCodes"));
    }
}
