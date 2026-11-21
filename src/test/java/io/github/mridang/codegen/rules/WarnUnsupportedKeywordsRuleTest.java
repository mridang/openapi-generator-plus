package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class WarnUnsupportedKeywordsRuleTest {

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
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n"
                                        + "components:\n  schemas:\n"
                                        + schemas,
                                null,
                                null)
                        .getOpenAPI();
        WarnUnsupportedKeywordsRule.check(api, log);
        return out;
    }

    @Test
    @DisplayName("each unsupported keyword warns once")
    void eachKeywordWarnsOnce() {
        final List<String> out =
                warnings("    A: {not: {type: string}}\n    B: {not: {type: integer}}\n"
                        + "    C: {type: object, patternProperties: {'^x': {type: string}}}\n");

        assertEquals(2, out.size());
    }

    @Test
    @DisplayName("a property named like a keyword does not warn")
    void aPropertyNamedLikeAKeywordDoesNotWarn() {
        assertEquals(
                0,
                warnings("    A: {type: object, properties: {not: {type: string}}}\n").size());
    }
}
