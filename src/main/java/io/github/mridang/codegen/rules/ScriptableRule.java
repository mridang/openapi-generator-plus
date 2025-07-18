package io.github.mridang.codegen.rules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.models.Info;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A normalization rule that loads a JS module via GraalVM,
 * with core OpenAPI model types pre‑bound into the JS context.
 */
public class ScriptableRule implements CustomNormalizationRule {

    /**
     * Applies the rule by loading and executing a JavaScript module.
     *
     * @param openAPI    the OpenAPI model to normalize
     * @param ruleConfig configuration map; expects the script path under "value"
     * @param logger     logger for errors and info
     */
    @Override
    @SuppressFBWarnings({"PATH_TRAVERSAL_IN", "REC_CATCH_EXCEPTION", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION"})
    public void apply(OpenAPI openAPI, Map<String, String> ruleConfig, Logger logger) {
        String scriptPathString = ruleConfig.get(RULE_VALUE_KEY);
        if (scriptPathString == null || scriptPathString.isBlank()) {
            throw new IllegalArgumentException(
                "ScriptableRule requires a non-empty '" + RULE_VALUE_KEY + "' configuration value."
            );
        }

        Path scriptPath = java.nio.file.Paths.get(scriptPathString);

        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException(
                "Script file does not exist: " + scriptPath.toAbsolutePath()
            );
        }

        if (!Files.isReadable(scriptPath)) {
            throw new IllegalStateException(
                "Script file is not readable: " + scriptPath.toAbsolutePath()
            );
        }

        try (Context ctx = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> true)
            .option("engine.WarnInterpreterOnly", "false")
            // *** this is critical for ES modules ***
            .option("js.esm-eval-returns-exports", "true")
            .build()) {

            Value bindings = ctx.getBindings("js");
            bindings.putMember("OpenAPI", OpenAPI.class);
            bindings.putMember("Info", Info.class);
            bindings.putMember("ExternalDocumentation", ExternalDocumentation.class);
            bindings.putMember("Server", Server.class);
            bindings.putMember("SecurityRequirement", SecurityRequirement.class);
            bindings.putMember("SecurityScheme", SecurityScheme.class);
            bindings.putMember("Tag", Tag.class);

            bindings.putMember("Paths", Paths.class);
            bindings.putMember("PathItem", PathItem.class);
            bindings.putMember("Components", Components.class);
            bindings.putMember("Schema", Schema.class);
            bindings.putMember("ApiResponse", ApiResponse.class);
            bindings.putMember("Parameter", Parameter.class);
            bindings.putMember("RequestBody", RequestBody.class);
            bindings.putMember("Example", Example.class);
            bindings.putMember("Header", Header.class);
            bindings.putMember("Link", Link.class);
            bindings.putMember("Callback", Callback.class);

            bindings.putMember("List", List.class);
            bindings.putMember("Map", Map.class);
            bindings.putMember("ArrayList", ArrayList.class);
            bindings.putMember("LinkedHashMap", LinkedHashMap.class);

            Source source = Source.newBuilder("js", scriptPath.toFile())
                .mimeType("application/javascript+module")
                .build();

            Value module = ctx.eval(source);
            Value applyFn = module.getMember("apply");
            if (applyFn == null || !applyFn.canExecute()) {
                throw new IllegalStateException("Script must export apply(openAPI, ruleConfig, logger)");
            }

            applyFn.execute(openAPI, ruleConfig, logger);
        } catch (Exception e) {
            logger.error("Failed to execute script at {}", scriptPath, e);
            throw new RuntimeException(e);
        }
    }
}
