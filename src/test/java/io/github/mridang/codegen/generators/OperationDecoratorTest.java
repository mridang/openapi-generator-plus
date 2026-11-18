package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
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
            "streamMethodName");

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
