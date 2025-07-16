package io.github.mridang.codegen.generators.php;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.languages.PhpClientCodegen;

import java.util.List;

/**
 * A custom PHP code generator that provides sane defaults for generating a
 * minimal, modern PHP client.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use 'camelCase' for variable naming conventions.</li>
 * <li>Allow additional properties in models for forward compatibility.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterPHPCodegen extends PhpClientCodegen implements UnsupportedFeaturesValidator {

    /**
     * Initializes a new instance of the {@code BetterPHPCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterPHPCodegen() {
        super();

        this.setLibrary(GUZZLE);
        this.setParameterNamingConvention("camelCase");
        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        additionalProperties.put(VARIABLE_NAMING_CONVENTION, "camelCase");

        setTemplateDir("templates/php");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return GUZZLE;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "php-plus".
     */
    @Override
    public String getName() {
        return "php-plus";
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
