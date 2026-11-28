package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Java quickstart, runs it against the mock server and compiles every {@code java}
 * fence of the README and SKILLS files. Each fence is a compilation unit in a package of its own
 * under {@code .out/fences}: a {@code file} fence is the body of the file after the package
 * clause, a {@code statements} fence has its imports hoisted above a class whose one method holds
 * the rest. The fences compile with {@code javac} against the SDK's classes and its dependencies,
 * with every lint on and warnings as errors.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaExamplesSpec extends AbstractExamplesSpec implements JavaSpec {

  @Override
  protected String getFenceLanguage() {
    return "java";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String name = fenceName(fence);
      String path = ".out/fences/" + name + "/Fence.java";
      String header = "package fences." + name + ";\n\n";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, header + fence.body()));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      for (String line : fence.body().split("\n", -1)) {
        if (line.startsWith("import ")) {
          imports.append(line).append('\n');
        } else {
          code.append(line.isEmpty() ? "" : "        ").append(line).append('\n');
        }
      }
      files.add(
          new HarnessFile(
              path,
              header
                  + imports
                  + "\nclass Fence {\n    static void fence() throws Exception {\n"
                  + code
                  + "    }\n}\n"));
    }
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "mvn compile -q -B"
          + " org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath"
          + " -Dmdep.outputFile=.out/fences.classpath",
      "javac -Xlint:all -Werror -d .out/fences-classes"
          + " -cp \"target/classes:$(cat .out/fences.classpath)\""
          + " $(find .out/fences -name '*.java')"
    };
  }
}
