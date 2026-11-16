package io.github.mridang.codegen.generators.go;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetterGoCodegenTest {

  private static Map<String, Object> arg(String kind) {
    final Map<String, Object> m = new HashMap<>();
    m.put("isPath", "path".equals(kind));
    m.put("isOptions", "options".equals(kind));
    m.put("isServer", "server".equals(kind));
    m.put("paramName", kind);
    return m;
  }

  private static List<Object> names(List<Map<String, Object>> args) {
    final List<Object> out = new ArrayList<>();
    for (final Map<String, Object> a : args) {
      out.add(a.get("paramName"));
    }
    return out;
  }

  @Test
  void movesTheServerInFrontOfTheOptionsObject() {
    final List<Map<String, Object>> args = new ArrayList<>();
    args.add(arg("path"));
    args.add(arg("options"));
    args.add(arg("server"));
    BetterGoCodegen.moveServerBeforeOptions(args);
    assertThat(names(args)).containsExactly("path", "server", "options");
  }

  @Test
  void leavesAServerOnlySignatureUnchanged() {
    final List<Map<String, Object>> args = new ArrayList<>();
    args.add(arg("path"));
    args.add(arg("server"));
    BetterGoCodegen.moveServerBeforeOptions(args);
    assertThat(names(args)).containsExactly("path", "server");
  }
}
