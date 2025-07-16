package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.languages.JavaClientCodegen;

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
     * all supporting files, ensuring a minimal code generation.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        this.supportingFiles.clear();
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
