package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Compiles the Elixir quickstart, runs it against the mock server and compiles every {@code
 * elixir} fence of the README and SKILLS files. Each fence becomes a file of its own under {@code
 * .out/fences}: a {@code file} fence as written, a {@code statements} fence indented into a
 * function of a module named after the fence, because Elixir allows no statement outside a
 * function in a compiled file. The README fences and the SKILLS fences are each compiled in one
 * {@code elixirc --warnings-as-errors} call (one VM start per file instead of one per fence, and
 * module names are unique within each markdown file), bounded by a timeout so a stalled compiler
 * fails the test instead of hanging it; a function or module a fence calls that does not exist
 * fails the build; the code path holds the test
 * environment's dependencies, because the OpenTelemetry recipe needs them. The files never reach
 * the golden: the working copy is restored before every command batch.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirExamplesSpec extends AbstractExamplesSpec implements ElixirSpec {

  @Override
  protected String getFenceLanguage() {
    return "elixir";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String path = ".out/fences/" + fenceName(fence) + ".ex";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, fence.body()));
        continue;
      }
      StringBuilder code = new StringBuilder();
      for (String line : fence.body().split("\n", -1)) {
        code.append(line.isEmpty() ? "" : "    ").append(line).append('\n');
      }
      String module = "Fence." + Character.toUpperCase(fenceName(fence).charAt(0))
          + fenceName(fence).substring(1).replace("_", "");
      files.add(
          new HarnessFile(path, "defmodule " + module + " do\n  def run do\n" + code + "  end\nend\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "MIX_ENV=test mix compile",
      "mkdir -p .out/fences/ebin && for group in readme skills; do"
          + " set -- .out/fences/${group}_*.ex; [ -e \"$1\" ] || continue;"
          + " timeout 600 elixirc --warnings-as-errors -o .out/fences/ebin"
          + " -pa \"_build/test/lib/*/ebin\" \"$@\";"
          + " code=$?; if [ $code -eq 143 ] || [ $code -eq 124 ]; then"
          + " echo \"elixirc timed out on the $group fences\"; fi;"
          + " [ $code -eq 0 ] || exit $code; done"
    };
  }
}
