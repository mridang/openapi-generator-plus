package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
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
     * non-essential supporting files while keeping the core infrastructure
     * needed for the API classes to function.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        // Keep all supporting files from parent - just remove build files
        supportingFiles.removeIf(f ->
            f.getDestinationFilename().equals("pom.xml") ||
            f.getDestinationFilename().equals("build.gradle") ||
            f.getDestinationFilename().equals("build.sbt") ||
            f.getDestinationFilename().equals("settings.gradle") ||
            f.getDestinationFilename().equals("gradle.properties") ||
            f.getDestinationFilename().equals("gradlew") ||
            f.getDestinationFilename().equals("gradlew.bat") ||
            f.getDestinationFilename().equals("README.md") ||
            f.getDestinationFilename().equals(".travis.yml") ||
            f.getDestinationFilename().equals(".gitignore") ||
            f.getDestinationFilename().equals("git_push.sh") ||
            f.getDestinationFilename().endsWith(".sbt")
        );
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
