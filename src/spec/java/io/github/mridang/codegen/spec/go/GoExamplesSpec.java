package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Go quickstart, runs it against the mock server and builds every {@code go} fence of
 * the README and SKILLS files. Each fence gets a package of its own under {@code .out/fences}; a
 * {@code file} fence is the body of the file after the package clause, a {@code statements}
 * fence has its imports hoisted and the rest placed in a function that returns an error.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoExamplesSpec extends AbstractExamplesSpec implements GoSpec {

  @Override
  protected String getFenceLanguage() {
    return "go";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String dir = ".out/fences/" + fenceName(fence);
      String body = fence.body();
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(dir + "/fence.go", "package fences\n\n" + body));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      boolean inBlock = false;
      for (String line : body.split("\n", -1)) {
        if (inBlock) {
          imports.append(line).append('\n');
          inBlock = !line.startsWith(")");
        } else if (line.startsWith("import (")) {
          imports.append(line).append('\n');
          inBlock = true;
        } else if (line.startsWith("import ")) {
          imports.append(line).append('\n');
        } else {
          code.append(line.isEmpty() ? "" : "\t").append(line).append('\n');
        }
      }
      files.add(
          new HarnessFile(
              dir + "/fence.go",
              "package fences\n\n"
                  + imports
                  + "\nfunc fence() error {\n"
                  + code
                  + "\treturn nil\n}\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"go build $(ls -d ./.out/fences/*/)"};
  }
}
