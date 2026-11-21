package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Swift quickstart, runs it against the mock server and type-checks every {@code swift}
 * fence of the README and SKILLS files. Each fence becomes a file of its own under {@code
 * .out/fences}: a {@code file} fence as written, a {@code statements} fence with its leading
 * {@code import} lines kept at the top and the rest placed in an {@code async throws} function,
 * because Swift allows top-level statements only in the main file. Each file is checked on its own
 * with {@code swiftc -typecheck} in the package's Swift 5 language mode against the modules the
 * package build left behind, the test target's dependencies included, so the OpenTelemetry recipe
 * resolves its import. The files never reach the golden: the working copy is restored before every
 * command batch.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftExamplesSpec extends AbstractExamplesSpec implements SwiftSpec {

  @Override
  protected String getFenceLanguage() {
    return "swift";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String path = ".out/fences/" + fenceName(fence) + ".swift";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, fence.body()));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      boolean header = true;
      for (String line : fence.body().split("\n", -1)) {
        if (header && (line.startsWith("import ") || line.isBlank())) {
          imports.append(line).append('\n');
          continue;
        }
        header = false;
        code.append(line.isEmpty() ? "" : "    ").append(line).append('\n');
      }
      files.add(
          new HarnessFile(
              path,
              imports.toString().strip()
                  + "\n\nfunc fence() async throws {\n"
                  + code
                  + "}\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "swift build --build-tests",
      "BIN=$(swift build --show-bin-path) && for f in .out/fences/*.swift; do"
          + " swiftc -typecheck -swift-version 5 -I \"$BIN/Modules\" -I \"$BIN\" \"$f\""
          + " || exit 1; done"
    };
  }
}
