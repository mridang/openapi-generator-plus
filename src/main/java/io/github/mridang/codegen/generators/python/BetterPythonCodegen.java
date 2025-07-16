package io.github.mridang.codegen.generators.python;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.PythonClientCodegen;

import java.io.File;
import java.util.List;

/**
 * A custom Python code generator that provides sane defaults for generating a
 * minimal, modern Python client.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use the 'urllib3' library for HTTP requests.</li>
 * <li>Set specific project, package, and version information.</li>
 * <li>Allow additional properties in models for forward compatibility.</li>
 * <li>Enable oneOf discriminator lookups.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterPythonCodegen extends PythonClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterPythonCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterPythonCodegen() {
        super();

        this.setLibrary(DEFAULT_LIBRARY);
        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setUseOneOfDiscriminatorLookup(true);

        setTemplateDir("templates/python");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return DEFAULT_LIBRARY;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "python-plus".
     */
    @Override
    public String getName() {
        return "python-plus";
    }

    /**
     * Processes generator options and then customizes the output by removing
     * all supporting files, ensuring a minimal code generation.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        this.supportingFiles.clear();

        String modelPath = modelPackage.replace('.', File.separatorChar);
        String apiPath = apiPackage.replace('.', File.separatorChar);
        supportingFiles.add(new SupportingFile("__init__model.mustache", modelPath, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__api.mustache", apiPath, "__init__.py"));

    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
