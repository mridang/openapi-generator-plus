package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ScriptableRule}, verifying that a JS module
 * which prefixes every OpenAPI path with "/foo" is applied correctly.
 */
public class ScriptableRuleTest extends BaseRuleTest<ScriptableRule> {

    private static final Logger logger = LoggerFactory.getLogger(ScriptableRuleTest.class);

    @Test
    @DisplayName("Should throw IllegalArgumentException when no script path provided")
    void shouldThrowWhenNoScriptConfigured() {
        OpenAPI openAPI = new OpenAPI().path("/a", new PathItem());
        assertThrows(IllegalArgumentException.class,
            () -> rule.apply(openAPI, Map.of(), logger),
            "Expected IllegalArgumentException when SCRIPT_VALUE_KEY is absent");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when script file does not exist")
    void shouldThrowWhenScriptFileMissing() {
        OpenAPI openAPI = new OpenAPI().path("/a", new PathItem());
        String missing = "does-not-exist.mjs";
        assertThrows(IllegalStateException.class,
            () -> rule.apply(openAPI, Map.of("value", missing), logger),
            "Expected IllegalStateException for missing script file");
    }

    @Test
    @DisplayName("Should execute JS module to prefix all paths with /foo")
    public void prefixesAllPathsWithFoo() throws Exception {
        OpenAPI openAPI = new OpenAPI()
            .path("/a", new PathItem())
            .path("/b", new PathItem());

        String jsModule =
            "export function apply(openAPI, ruleConfig, logger) {\n" +
                "  const original = openAPI.getPaths();\n" +
                "  const updated  = new Paths();\n" +
                "  for (const entry of original.entrySet()) {\n" +
                "    updated.put('/foo' + entry.getKey(), entry.getValue());\n" +
                "  }\n" +
                "  openAPI.setPaths(updated);\n" +
                "}\n";

        Path scriptFile = Files.createTempFile("prefix-rule", ".mjs");
        Files.writeString(scriptFile, jsModule);

        ScriptableRule rule = new ScriptableRule();
        Map<String, String> config = Map.of("value", scriptFile.toString());
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).hasPaths("/foo/a", "/foo/b");
    }
}
