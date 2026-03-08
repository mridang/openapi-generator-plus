package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import java.io.File;
import java.util.List;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.CSharpClientCodegen;

/**
 * A custom C# code generator that provides sane defaults for generating a
 * minimal, modern C# client.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use System.Text.Json for JSON serialization.</li>
 * <li>Use HttpClient for HTTP requests.</li>
 * <li>Target .NET 9.0.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterCSharpCodegen extends CSharpClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterCSharpCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterCSharpCodegen() {
        super();

        this.setLibrary(GENERICHOST);
        this.setDisallowAdditionalPropertiesIfNotPresent(false);

        setTemplateDir("templates/csharp");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return GENERICHOST;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "csharp-plus".
     */
    @Override
    public String getName() {
        return "csharp-plus";
    }

    /**
     * Processes generator options and then customizes the output by removing
     * non-essential supporting files while keeping the core infrastructure
     * needed for the API classes to function.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        this.modelTemplateFiles.clear();
        this.modelTemplateFiles.put("models/model.mustache", ".cs");
        this.apiTemplateFiles.clear();
        this.apiTemplateFiles.put("api/api.mustache", ".cs");
        this.apiTestTemplateFiles.clear();
        this.modelTestTemplateFiles.clear();
        this.apiDocTemplateFiles.clear();
        this.modelDocTemplateFiles.clear();
        this.supportingFiles.clear();

        // Override model package from "Model" to "Models" for consistency
        this.setModelPackage("Models");

        String invokerFolder =
                sourceFolder + File.separator + packageName.replace(".", File.separator);

        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.cs"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + apiPackage,
                        "BaseApi.cs"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.cs"));
    }

    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
