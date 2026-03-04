package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.TypeScriptFetchClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;

import java.util.List;
import java.util.Map;

/**
 * A custom TypeScript code generator that provides sane defaults for generating
 * a minimal, modern TypeScript client using the Fetch API.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Target the 'fetch' platform with ES6 support.</li>
 * <li>Preserve original model property naming.</li>
 * <li>Allow additional properties in models for forward compatibility.</li>
 * <li>Generate a single parameter object for API methods.</li>
 * <li>Use a '.js' extension for imports to support modern ESM workflows.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterNodeCodegen extends TypeScriptFetchClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterNodeCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterNodeCodegen() {
        super();

        this.setSupportsES6(true);
        this.setEnsureUniqueParams(true);
        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setEnumUnknownDefaultCase(true);
        this.setImportFileExtension(".js");
        additionalProperties.put(CodegenConstants.MODEL_PROPERTY_NAMING, "original");
        additionalProperties.put(WITH_INTERFACES, false);
        additionalProperties.put(USE_SINGLE_REQUEST_PARAMETER, false);
        additionalProperties.put(FILE_NAMING, "kebab-case");
        additionalProperties.put(USE_SQUARE_BRACKETS_IN_ARRAY_NAMES, true);

        setTemplateDir("templates/node");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return "typescript-fetch";
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "node-plus".
     */
    @Override
    public String getName() {
        return "node-plus";
    }

    /**
     * Processes generator options and then customizes the output by removing
     * all supporting files, ensuring a minimal code generation.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        this.supportingFiles.clear();
        this.apiPackage = "api";
        supportingFiles.add(new SupportingFile("ApiClient.mustache", "", "ApiClient.ts"));
        supportingFiles.add(new SupportingFile("DefaultApiClient.mustache", "", "DefaultApiClient.ts"));
        supportingFiles.add(new SupportingFile("ApiResponse.mustache", "", "ApiResponse.ts"));
        supportingFiles.add(new SupportingFile("BaseApi.mustache", "api", "BaseApi.ts"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", "", "ObjectSerializer.ts"));
        supportingFiles.add(new SupportingFile("HeaderSelector.mustache", "", "HeaderSelector.ts"));
    }

    /**
     * Post-processes all models to add {@code x-zod-type} vendor extensions to each
     * property, enabling the Mustache template to generate Zod schemas.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap models : result.values()) {
            for (ModelMap model : models.getModels()) {
                CodegenModel cm = model.getModel();
                for (CodegenProperty prop : cm.vars) {
                    String zodType = computeZodType(prop);
                    if (!prop.required) {
                        zodType += ".optional()";
                    }
                    if (prop.isNullable) {
                        zodType += ".nullable()";
                    }
                    prop.vendorExtensions.put("x-zod-type", zodType);
                }
            }
        }
        return result;
    }

    /**
     * Computes the Zod type expression for a given CodegenProperty.
     * This maps OpenAPI types to their Zod equivalents, handling primitives,
     * arrays, maps, and model references.
     */
    private String computeZodType(CodegenProperty prop) {
        if (prop.isArray) {
            String inner = prop.items != null ? computeZodType(prop.items) : "z.any()";
            return "z.array(" + inner + ")";
        }
        if (prop.isMap) {
            String inner = prop.items != null ? computeZodType(prop.items) : "z.any()";
            return "z.record(z.string(), " + inner + ")";
        }
        if (prop.isString || prop.isDate || prop.isDateTime) {
            return "z.string()";
        }
        if (prop.isInteger || prop.isLong) {
            return "z.number()";
        }
        if (prop.isFloat || prop.isDouble || prop.isNumber) {
            return "z.number()";
        }
        if (prop.isBoolean) {
            return "z.boolean()";
        }
        if (prop.isFreeFormObject) {
            return "z.record(z.string(), z.any())";
        }
        if (!prop.isPrimitiveType && prop.complexType != null) {
            return "z.lazy(() => " + prop.complexType + "Schema)";
        }
        return "z.any()";
    }

    @Override
    public ExtendedCodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
