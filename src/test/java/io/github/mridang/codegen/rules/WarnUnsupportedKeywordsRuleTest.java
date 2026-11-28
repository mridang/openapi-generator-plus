package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WarnUnsupportedKeywordsRuleTest {

    private static final Logger SILENT = LoggerFactory.getLogger(WarnUnsupportedKeywordsRuleTest.class);

    private static OpenAPI spec(String schemas) {
        return new OpenAPIV3Parser()
                .readContents(
                        "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                                + "components:\n  schemas:\n"
                                + schemas,
                        null,
                        null)
                .getOpenAPI();
    }

    private List<String> warnings(String schemas) {
        final List<String> out = new ArrayList<>();
        final Logger log =
                (Logger)
                        Proxy.newProxyInstance(
                                Logger.class.getClassLoader(),
                                new Class<?>[] {Logger.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("warn") && args != null) {
                                        out.add(String.valueOf(args[0]) + " " + args[1]);
                                    }
                                    return null;
                                });
        WarnUnsupportedKeywordsRule.check(spec(schemas), false, log);
        return out;
    }

    @Test
    @DisplayName("each unsupported keyword warns once")
    void eachKeywordWarnsOnce() {
        final List<String> out =
                warnings("    A: {not: {type: string}}\n    B: {not: {type: integer}}\n"
                        + "    C: {type: object, propertyNames: {pattern: '^x'}}\n");

        assertEquals(2, out.size());
    }

    @Test
    @DisplayName("maxContains and the dynamic references warn, each once")
    void dynamicReferencesAndMaxContainsWarn() {
        final List<String> out =
                warnings("    A: {type: array, items: {type: string}, maxContains: 2}\n"
                        + "    B: {$dynamicAnchor: node, type: object,"
                        + " properties: {next: {$dynamicRef: '#node'}}}\n");

        assertEquals(3, out.size());
        assertTrue(String.join(" ", out).contains("$dynamicRef"));
    }

    @Test
    @DisplayName("a prefixItems tuple warns, so the strict mode refuses its untyped list")
    void prefixItemsWarns() {
        final List<String> out = warnings("    T: {type: array, prefixItems: [{type: number}, {type: string}]}\n");

        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("prefixItems"));
    }

    @Test
    @DisplayName("a property named like a keyword does not warn")
    void aPropertyNamedLikeAKeywordDoesNotWarn() {
        assertEquals(
                0,
                warnings("    A: {type: object, properties: {not: {type: string}}}\n").size());
    }

    @Test
    @DisplayName("strict mode fails the run on an unsupported keyword")
    void strictModeFailsOnAnUnsupportedKeyword() {
        final OpenAPI api = spec("    A: {not: {type: string}}\n");

        assertThrows(
                IllegalStateException.class,
                () -> WarnUnsupportedKeywordsRule.check(api, true, SILENT));
    }

    @Test
    @DisplayName("the default stays lenient on an unsupported keyword")
    void theDefaultStaysLenient() {
        final OpenAPI api = spec("    A: {not: {type: string}}\n");

        assertDoesNotThrow(() -> WarnUnsupportedKeywordsRule.check(api, false, SILENT));
    }

    @Test
    @DisplayName("strict mode passes a document without unsupported keywords")
    void strictModePassesACleanDocument() {
        final OpenAPI api = spec("    A: {type: object, properties: {not: {type: string}}}\n");

        assertDoesNotThrow(() -> WarnUnsupportedKeywordsRule.check(api, true, SILENT));
    }
}
