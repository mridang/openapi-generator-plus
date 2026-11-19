package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Ruby quickstart, runs it against the mock server and checks every {@code ruby} fence
 * of the README and SKILLS files. Each fence becomes a file of its own under {@code .out/fences}
 * behind the frozen-string-literal magic comment; Ruby allows statements and {@code require} at
 * the top of a file, so neither kind needs a shell or hoisting. Every file is syntax-checked with
 * {@code ruby -c} and the directory is type-checked by Steep against the shipped {@code sig/}
 * with the default diagnostics, through a harness Steepfile in the same directory. The one
 * diagnostic turned off is {@code UndeclaredMethodDefinition}: a fence that defines a method (the
 * operation fences, the fake authenticator) is the reader's own code, whose signature is not part
 * of the SDK. The working copy is restored before every command batch, so the files never reach
 * the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyExamplesSpec extends AbstractExamplesSpec implements RubySpec {

  private static final String STEEPFILE =
      "# frozen_string_literal: true\n"
          + "\n"
          + "D = Steep::Diagnostic\n"
          + "\n"
          + "target :fences do\n"
          + "  check '.'\n"
          + "  signature '../../sig'\n"
          + "  library 'base64', 'date', 'digest', 'json', 'securerandom', 'time', 'cgi', 'uri',"
          + " 'zlib', 'stringio', 'openssl', 'timeout'\n"
          + "  configure_code_diagnostics(D::Ruby.default) do |hash|\n"
          + "    hash[D::Ruby::UndeclaredMethodDefinition] = nil\n"
          + "  end\n"
          + "end\n";

  @Override
  protected String getFenceLanguage() {
    return "ruby";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    for (Fence fence : fences) {
      files.add(
          new HarnessFile(
              ".out/fences/" + fenceName(fence) + ".rb",
              "# frozen_string_literal: true\n\n" + fence.body()));
    }
    files.add(new HarnessFile(".out/fences/Steepfile", STEEPFILE));
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {
      "find .out/fences -name '*.rb' -print0 | xargs -0 -n1 ruby -c",
      "bundle exec steep check --steepfile=.out/fences/Steepfile"
    };
  }
}
