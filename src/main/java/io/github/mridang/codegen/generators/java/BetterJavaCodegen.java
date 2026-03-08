package io.github.mridang.codegen.generators.java;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterJavaCodegen.class);

    private final Formatter formatter;

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

        // Enable post-processing so that postProcessFile is called for each generated file
        setEnablePostProcessFile(true);

        formatter =
                new Formatter(
                        JavaFormatterOptions.builder()
                                .style(JavaFormatterOptions.Style.GOOGLE)
                                .build());
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

        this.modelTemplateFiles.clear();
        this.modelTemplateFiles.put("models/model.mustache", ".java");
        this.apiTemplateFiles.clear();
        this.apiTemplateFiles.put("api/api.mustache", ".java");

        // Clear all parent supporting files — we provide our own minimal set
        supportingFiles.clear();

        // Re-enable post-processing after super.processOpts() may have reset it
        setEnablePostProcessFile(true);

        String invokerFolder =
                sourceFolder + File.separator + invokerPackage.replace(".", File.separator);
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.java"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.java"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.java"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.java"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + "api",
                        "BaseApi.java"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.java"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.java"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.java"));
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".java")) {
            return;
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String formatted = formatter.formatSource(source);
            Files.write(file.toPath(), formatted.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Failed to read/write file for formatting: {}", file.getAbsolutePath(), e);
        } catch (FormatterException e) {
            LOGGER.warn("Failed to format file: {}", file.getAbsolutePath(), e);
        }
    }

    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
