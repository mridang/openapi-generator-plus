package io.github.mridang.codegen.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IgnoreReservedHeadersRuleTest extends BaseRuleTest<IgnoreReservedHeadersRule> {

    @Test
    @DisplayName("header parameters named Accept, Content-Type or Authorization are dropped in any case")
    void reservedHeadersAreDropped() {
        final OpenAPI api =
                new OpenAPIV3Parser()
                        .readContents(
                                "openapi: 3.0.3\ninfo: {title: t, version: '1'}\n"
                                        + "paths:\n  /a:\n    parameters:\n"
                                        + "      - {name: AUTHORIZATION, in: header, schema: {type: string}}\n"
                                        + "    get:\n      operationId: a\n      parameters:\n"
                                        + "        - {name: accept, in: header, schema: {type: string}}\n"
                                        + "        - {name: Content-Type, in: header, schema: {type: string}}\n"
                                        + "        - $ref: '#/components/parameters/Auth'\n"
                                        + "        - {name: X-Trace, in: header, schema: {type: string}}\n"
                                        + "        - {name: accept, in: query, schema: {type: string}}\n"
                                        + "      responses:\n        '200': {description: ok}\n"
                                        + "components:\n  parameters:\n    Auth: {name: Authorization, in: header, schema: {type: string}}\n",
                                null,
                                null)
                        .getOpenAPI();
        rule.apply(api, Map.of(), logger);

        final var item = Objects.requireNonNull(api.getPaths().get("/a"));
        assertEquals(0, Objects.requireNonNull(item.getParameters()).size());
        final var names =
                Objects.requireNonNull(item.getGet().getParameters()).stream().map(p -> p.getName()).collect(java.util.stream.Collectors.toList());
        assertEquals(java.util.List.of("X-Trace", "accept"), names);
    }
}
