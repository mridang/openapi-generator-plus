package io.github.mridang.codegen.generators.swift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Unit tests for the Swift-specific operation signature ordering. */
class BetterSwiftCodegenTest {

    private static Map<String, Object> arg(String kind) {
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("isOptions", "options".equals(kind));
        m.put("isServer", "server".equals(kind));
        return m;
    }

    private static List<String> kinds(List<Map<String, Object>> args) {
        return args.stream().map(a -> (String) a.get("kind")).collect(Collectors.toList());
    }

    /* Security mints an options argument, which must come after the server. */
    @Test
    void serverComesBeforeOptions() {
        final List<Map<String, Object>> in = List.of(arg("path"), arg("options"), arg("server"));
        assertEquals(
                List.of("path", "server", "options"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(in)));
    }

    @Test
    void listsWithoutBothAreUnchanged() {
        assertEquals(
                List.of("path", "server"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(List.of(arg("path"), arg("server")))));
        assertEquals(
                List.of("path", "options"),
                kinds(BetterSwiftCodegen.serverBeforeOptions(List.of(arg("path"), arg("options")))));
    }

    @Test
    void alreadyOrderedListIsUnchanged() {
        assertEquals(
                List.of("body", "server", "options"),
                kinds(
                        BetterSwiftCodegen.serverBeforeOptions(
                                List.of(arg("body"), arg("server"), arg("options")))));
    }
}
