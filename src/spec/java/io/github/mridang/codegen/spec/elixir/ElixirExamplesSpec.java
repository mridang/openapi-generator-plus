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
 * function of a module, because Elixir allows no statement outside a function in a compiled file.
 * Each file is compiled on its own with {@code elixirc --warnings-as-errors}, so a function or
 * module a fence calls that does not exist fails the build; the code path holds the test
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
      files.add(new HarnessFile(path, "defmodule Fence do\n  def run do\n" + code + "  end\nend\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "MIX_ENV=test mix compile",
      "mkdir -p .out/fences/ebin && for f in .out/fences/*.ex; do"
          + " elixirc --warnings-as-errors -o .out/fences/ebin"
          + " -pa \"_build/test/lib/*/ebin\" \"$f\" || exit 1; done"
    };
  }
}
