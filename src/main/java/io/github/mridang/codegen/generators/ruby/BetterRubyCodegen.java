package io.github.mridang.codegen.generators.ruby;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.languages.RubyClientCodegen;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import static org.openapitools.codegen.utils.StringUtils.underscore;

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

    /**
     * Overrides the default behavior to create a directory structure that
     * respects Ruby namespaces defined in the {@code moduleName}.
     * <p>
     * The default implementation bases the path on the sanitized {@code gemName},
     * which flattens namespaces (e.g., 'My::Module' becomes 'my_module').
     * This method correctly converts '::' in the module name to a path
     * separator, creating the expected nested directory structure (e.g.,
     * 'lib/my/module/models').
     *
     * @return The correctly nested path to the model files.
     */
    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public String modelFileFolder() {
        String path = moduleName.replaceAll("::", "/");
        return Paths.get(getOutputDir(), libFolder, underscore(path), modelPackage().replace(".", File.separator))
            .toString();
    }

    /**
     * Overrides the default behavior to create a directory structure that
     * respects Ruby namespaces defined in the {@code moduleName}.
     * <p>
     * The default implementation bases the path on the sanitized {@code gemName},
     * which flattens namespaces (e.g., 'My::Module' becomes 'my_module').
     * This method correctly converts '::' in the module name to a path
     * separator, creating the expected nested directory structure (e.g.,
     * 'lib/my/module/api').
     *
     * @return The correctly nested path to the API files.
     */
    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public String apiFileFolder() {
        String path = moduleName.replaceAll("::", "/");
        return Paths.get(getOutputDir(), libFolder, underscore(path), apiPackage().replace(".", File.separator))
            .toString();
    }

    /**
     * Converts a model class name to a Zeitwerk-compatible snake_case file name.
     * For example, 'MyOIDCModel' becomes 'my_o_i_d_c_model'.
     *
     * @param name The name of the model.
     * @return The model's filename (without the .rb extension).
     */
    @Override
    public String toModelFilename(String name) {
        String modelName = super.toModelName(name);
        return toZeitwerkFilename(modelName);
    }

    /**
     * Converts an API class name to a Zeitwerk-compatible snake_case file name.
     * For example, 'DefaultApi' becomes 'default_api'.
     *
     * @param name The name of the API.
     * @return The API's filename (without the .rb extension).
     */
    @Override
    public String toApiFilename(final String name) {
        String apiName = super.toApiName(name);
        return toZeitwerkFilename(apiName);
    }

    /**
     * Implements a custom underscoring logic that correctly handles acronyms
     * for Zeitwerk compatibility. It prepends an underscore to all capital
     * letters and then converts the result to lowercase.
     *
     * @param name The CamelCase or PascalCase class name.
     * @return A Zeitwerk-compatible snake_case string.
     */
    private String toZeitwerkFilename(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String result = name.replaceAll("([A-Z])", "_$1").replaceAll("^_", "");
        return result.toLowerCase(Locale.ROOT);
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
