package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.languages.TypeScriptFetchClientCodegen;

import java.util.List;

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
    }

    @Override
    public ExtendedCodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
