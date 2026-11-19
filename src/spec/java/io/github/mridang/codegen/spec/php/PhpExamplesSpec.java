package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the PHP quickstart, runs it against the mock server and checks every {@code php} fence
 * of the README and SKILLS files. Each fence becomes a file of its own under {@code .out/fences}
 * in a namespace of its own, so the functions two fences declare never clash: a {@code file}
 * fence follows the namespace line unchanged, a {@code statements} fence has its {@code use}
 * imports hoisted above a function {@code fence()} that holds the rest. Every file is linted with
 * {@code php -l} and the directory is analysed by PHPStan with the shipped {@code phpstan.neon}
 * (level 9), so an undeclared variable or a wrong signature fails the build. The working copy is
 * restored before every command batch, so the files never reach the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpExamplesSpec extends AbstractExamplesSpec implements PhpSpec {

  @Override
  protected String getFenceLanguage() {
    return "php";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      String name = fenceName(fence);
      String header =
          "<?php\n\ndeclare(strict_types=1);\n\nnamespace Fences\\" + namespace(name) + ";\n\n";
      String path = ".out/fences/" + name + ".php";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, header + fence.body()));
        continue;
      }
      StringBuilder imports = new StringBuilder();
      StringBuilder code = new StringBuilder();
      for (String line : fence.body().split("\n", -1)) {
        if (line.startsWith("use ")) {
          imports.append(line).append('\n');
        } else {
          code.append(line.isEmpty() ? "" : "    ").append(line).append('\n');
        }
      }
      files.add(
          new HarnessFile(
              path,
              header
                  + imports
                  + "\nfunction fence(): void\n{\n"
                  + code.toString().strip().replaceFirst("^", "    ")
                  + "\n}\n"));
    }
    return files;
  }

  /** {@code readme_03} becomes {@code Readme03}. */
  private static String namespace(String name) {
    StringBuilder out = new StringBuilder();
    for (String part : name.split("_", -1)) {
      if (!part.isEmpty()) {
        out.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
      }
    }
    return out.toString();
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "find .out/fences -name '*.php' -print0 | xargs -0 -n1 php -l",
      "vendor/bin/phpstan analyse --no-progress --memory-limit=-1 .out/fences"
    };
  }
}
