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
            "effectiveProduces",
            "optionsClassName",
            "serverClassName",
            "apiClassName",
            "optionsParamRequired",
            "hasOptionsParam",
            "hasPerOperationServer",
            "requestBodyKind",
            "returnKind",
            "hasReturnType",
            "hasQueryParams",
            "hasHeaderParams",
            "hasFormParams",
            "hasCookieParams",
            "hasPathParams",
            "hasBodyParam");

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
}
