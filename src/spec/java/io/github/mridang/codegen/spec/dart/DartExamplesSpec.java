package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Analyses the Dart quickstart, runs it against the mock server and analyses every {@code dart}
 * fence of the README and SKILLS files. Each fence becomes a library of its own under {@code
 * .out/fences}: a {@code file} fence as written, a {@code statements} fence with its leading
 * {@code import} directives kept at the top and the rest placed in an {@code async} function,
 * because Dart allows no statement at the top level. The files sit inside the package, so they
 * resolve {@code package:} imports through the package config and are analysed with the
 * package's {@code analysis_options.yaml} and the severity {@code make examples} uses. The
 * working copy is restored before every command batch, so the files never reach the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartExamplesSpec extends AbstractExamplesSpec implements DartSpec {

  @Override
  protected String getFenceLanguage() {
    return "dart";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String path = ".out/fences/" + fenceName(fence) + ".dart";
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
        code.append(line).append('\n');
      }
      files.add(
          new HarnessFile(
              path,
              imports.toString().strip()
                  + "\n\nFuture<void> fence() async {\n"
                  + code
                  + "}\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"dart analyze --fatal-warnings --fatal-infos .out/fences"};
  }
}
