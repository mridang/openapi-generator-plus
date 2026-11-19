package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Python quickstart, runs it against the mock server and type-checks every {@code
 * python} fence of the README and SKILLS files. A {@code file} fence is a module; a {@code
 * statements} fence has its imports hoisted and the rest placed in an async function.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-python")
public class PythonExamplesSpec extends AbstractExamplesSpec implements PythonSpec {

  @Override
  protected String getFenceLanguage() {
    return "python";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String path = ".out/fences/fence_" + fenceName(fence) + ".py";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, fence.body()));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      boolean inImport = false;
      for (String line : fence.body().split("\n", -1)) {
        if (inImport) {
          imports.append(line).append('\n');
          inImport = !line.startsWith(")");
        } else if (line.startsWith("import ") || line.startsWith("from ")) {
          imports.append(line).append('\n');
          inImport = line.endsWith("(");
        } else {
          code.append(line.isEmpty() ? "" : "    ").append(line).append('\n');
        }
      }
      files.add(
          new HarnessFile(
              path, imports + "\n\nasync def fence() -> None:\n" + code.toString().stripTrailing() + "\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"mypy .out/fences/"};
  }
}
