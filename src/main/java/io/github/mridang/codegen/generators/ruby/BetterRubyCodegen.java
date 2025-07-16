package io.github.mridang.codegen.generators.ruby;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.languages.RubyClientCodegen;

import java.util.List;

/**
 * A custom Ruby code generator that provides sane defaults for generating a
 * minimal, modern Ruby client using the Typhoeus HTTP library.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use the 'typhoeus' library for HTTP requests.</li>
 * <li>Set a default module name to 'Opigen::Client'.</li>
 * <li>Allow additional properties in models for forward compatibility.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterRubyCodegen extends RubyClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterRubyCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterRubyCodegen() {
        super();

        this.setLibrary(TYPHOEUS);
        this.setModuleName("Opigen::Client");
        this.setDisallowAdditionalPropertiesIfNotPresent(false);

        setTemplateDir("templates/ruby");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return TYPHOEUS;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "ruby-plus".
     */
    @Override
    public String getName() {
        return "ruby-plus";
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
