package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the TypeScript quickstart, runs it against the mock server and type-checks every {@code
 * typescript} fence of the README and SKILLS files. Each fence becomes a module of its own under
 * {@code .out/fences}: a {@code file} fence as written, a {@code statements} fence with its leading
 * {@code import} declarations kept at the top and the rest placed in an {@code async} function,
 * because the package compiles to CommonJS, where top-level {@code await} is not allowed. The
 * fences import the SDK by its package name, which resolves through the package's own {@code
 * exports} to the built {@code dist}, so the package is built first and the fences are checked
 * with the compiler options of {@code tsconfig.examples.json} through a harness tsconfig in the
 * same directory. The working copy is restored before every command batch, so the files never
 * reach the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-node")
public class NodeExamplesSpec extends AbstractExamplesSpec implements NodeSpec {

  private static final String TSCONFIG =
      "{\n"
          + "  \"extends\": \"../../tsconfig.examples.json\",\n"
          + "  \"compilerOptions\": { \"noEmit\": true, \"rootDir\": \".\" },\n"
          + "  \"include\": [\"*.ts\"]\n"
          + "}\n";

  @Override
  protected String getFenceLanguage() {
    return "typescript";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String path = ".out/fences/" + fenceName(fence) + ".ts";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, fence.body() + "\nexport {};\n"));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      boolean header = true;
      boolean inImport = false;
      for (String line : fence.body().split("\n", -1)) {
        if (header && (inImport || line.startsWith("import ") || line.isBlank())) {
          imports.append(line).append('\n');
          if (line.startsWith("import ")) {
            inImport = true;
          }
          if (inImport && line.stripTrailing().endsWith(";")) {
            inImport = false;
          }
          continue;
        }
        header = false;
        code.append(line).append('\n');
      }
      files.add(
          new HarnessFile(
              path,
              imports.toString().strip()
                  + "\n\nexport async function fence(): Promise<void> {\n"
                  + code
                  + "}\n"));
    }
    files.add(new HarnessFile(".out/fences/tsconfig.json", TSCONFIG));
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"make build", "npx tsc -p .out/fences/tsconfig.json"};
  }
}
