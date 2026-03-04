package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.JavaClientCodegen;

import java.io.File;
import java.util.List;

/**
 * A custom Java code generator that provides sane defaults for generating a
 * minimal, modern Java client.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use the Apache HttpClient library for HTTP requests.</li>
 * <li>Use Jackson for JSON serialization.</li>
 * <li>Use the Java 8 Date/Time library (java.time.*).</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterJavaCodegen extends JavaClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterJavaCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterJavaCodegen() {
        super();

        this.setLibrary(APACHE);
        this.setSerializationLibrary(SERIALIZATION_LIBRARY_JACKSON);
        this.setDateLibrary("java8");

        setTemplateDir("templates/java");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return APACHE;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "java-plus".
     */
    @Override
    public String getName() {
        return "java-plus";
    }

    /**
     * Processes generator options and then customizes the output by removing
     * non-essential supporting files while keeping the core infrastructure
     * needed for the API classes to function.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        // Clear all parent supporting files — we provide our own minimal set
        supportingFiles.clear();

        String invokerFolder = sourceFolder + File.separator
            + invokerPackage.replace(".", File.separator);
        supportingFiles.add(new SupportingFile(
            "apiException.mustache", invokerFolder, "ApiException.java"));
        supportingFiles.add(new SupportingFile(
            "ApiClient.mustache", invokerFolder, "ApiClient.java"));
        supportingFiles.add(new SupportingFile(
            "DefaultApiClient.mustache", invokerFolder, "DefaultApiClient.java"));
        supportingFiles.add(new SupportingFile(
            "ApiResponse.mustache", invokerFolder, "ApiResponse.java"));
        supportingFiles.add(new SupportingFile(
            "BaseApi.mustache", invokerFolder, "BaseApi.java"));
        supportingFiles.add(new SupportingFile(
            "Configuration.mustache", invokerFolder, "Configuration.java"));
        supportingFiles.add(new SupportingFile(
            "object_serializer.mustache", invokerFolder, "ObjectSerializer.java"));
        supportingFiles.add(new SupportingFile(
            "header_selector.mustache", invokerFolder, "HeaderSelector.java"));
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
