package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.TypeScriptFetchClientCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Logger log = LoggerFactory.getLogger(BetterNodeCodegen.class);

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

    @Override
    public String getLibrary() {
        return "typescript-fetch";
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
        setEnablePostProcessFile(true);
        this.modelTemplateFiles.clear();
        this.modelTemplateFiles.put("model.mustache", ".ts");
        this.supportingFiles.clear();
        this.apiPackage = "api";
        supportingFiles.add(new SupportingFile("api_client.mustache", "", "ApiClient.ts"));
        supportingFiles.add(new SupportingFile("default_api_client.mustache", "", "DefaultApiClient.ts"));
        supportingFiles.add(new SupportingFile("api_response.mustache", "", "ApiResponse.ts"));
        supportingFiles.add(new SupportingFile("configuration.mustache", "", "Configuration.ts"));
        supportingFiles.add(new SupportingFile("base_api.mustache", "api", "BaseApi.ts"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", "", "ObjectSerializer.ts"));
        supportingFiles.add(new SupportingFile("header_selector.mustache", "", "HeaderSelector.ts"));
    }


    /**
     * Overrides the type declaration to remove semicolons from index signatures
     * in map types, producing {@code { [key: string]: T }} instead of
     * {@code { [key: string]: T; }} for prettier compatibility.
     */
    @Override
    public String getTypeDeclaration(io.swagger.v3.oas.models.media.Schema p) {
        String type = super.getTypeDeclaration(p);
        // Remove trailing semicolons inside index signature types: { [key: string]: T; } -> { [key: string]: T }
        return type.replaceAll(";\\s*}", " }");
    }

    private static final int PRINT_WIDTH = 120;

    /**
     * Pattern to match a TypeScript function declaration whose signature exceeds
     * the print width. Captures the indent, function prefix (including name and
     * opening paren), the parameters, the closing paren with return type, and
     * the opening brace.
     */
    private static final Pattern FUNC_SIG_PATTERN =
        Pattern.compile("^(\\s*)(export function \\w+\\()(.+)(\\): \\w+ \\{)$");

    /**
     * Pattern to match a chained .replace() call that exceeds the print width.
     * Captures the prefix up to .replace(, the first argument, comma, and the
     * second argument followed by closing paren and semicolon.
     */
    private static final Pattern REPLACE_PATTERN =
        Pattern.compile("^(\\s*const path = .+)\\.replace\\((.+), (.+)\\);$");

    /**
     * Post-processes each generated TypeScript file to wrap lines that exceed
     * the 120-character print width. This handles function signatures and
     * chained .replace() calls that may exceed the limit depending on the
     * lengths of generated class names and URL paths.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null || !file.getName().endsWith(".ts")) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>(lines.size());
            boolean changed = false;

            for (String line : lines) {
                if (line.equals("/* tslint:disable */") || line.equals("/* eslint-disable */")) {
                    changed = true;
                    continue;
                }
                // Remove blank lines between decorators and properties
                if (line.trim().isEmpty() && !result.isEmpty()) {
                    String prevLine = result.get(result.size() - 1).trim();
                    if (prevLine.startsWith("@Expose") || prevLine.startsWith("@Type")) {
                        changed = true;
                        continue;
                    }
                }
                if (line.length() >= PRINT_WIDTH) {
                    String wrapped = wrapLongLine(line);
                    if (!wrapped.equals(line)) {
                        changed = true;
                        // Split the wrapped result into multiple lines to add to result
                        for (String wrappedLine : wrapped.split("\n")) {
                            result.add(wrappedLine);
                        }
                        continue;
                    }
                }
                result.add(line);
            }

            // Remove trailing blank lines
            while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty()) {
                result.remove(result.size() - 1);
                changed = true;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.debug("Failed to post-process {}: {}", file.getName(), e.getMessage());
        }
    }

    /**
     * Attempt to wrap a long line according to prettier conventions.
     * Handles function signatures and .replace() chains.
     *
     * @param line the line to potentially wrap
     * @return the wrapped line (with embedded newlines) or the original line
     */
    private String wrapLongLine(String line) {
        // Try to wrap a function signature: export function Foo(param1: T, param2: U): R {
        Matcher funcMatcher = FUNC_SIG_PATTERN.matcher(line);
        if (funcMatcher.matches()) {
            String indent = funcMatcher.group(1);
            String funcPrefix = funcMatcher.group(2);
            String params = funcMatcher.group(3);
            String suffix = funcMatcher.group(4);
            String paramIndent = indent + "  ";
            String[] paramList = params.split(", ");
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append(funcPrefix).append("\n");
            for (int i = 0; i < paramList.length; i++) {
                sb.append(paramIndent).append(paramList[i].trim());
                if (i < paramList.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(indent).append(suffix);
            return sb.toString();
        }

        // Try to wrap a .replace() chain
        Matcher replaceMatcher = REPLACE_PATTERN.matcher(line);
        if (replaceMatcher.matches()) {
            String prefix = replaceMatcher.group(1);
            String arg1 = replaceMatcher.group(2);
            String arg2 = replaceMatcher.group(3);
            String indent = line.substring(0, line.indexOf(line.trim()));
            String argIndent = indent + "  ";
            return prefix + ".replace(\n" + argIndent + arg1.trim() + ",\n" + argIndent + arg2.trim() + "\n" + indent + ");";
        }

        return line;
    }

    @Override
    public ExtendedCodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}
