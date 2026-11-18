package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.FenceExtractor;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Base class for the fence gate. Reads {@code README.md} and {@code SKILLS.md} from the golden,
 * takes every fence tagged with the SDK language, checks that each declares its kind ({@code
 * file} or {@code statements}), wraps them with the language's harness, writes the wrapped files
 * under the SDK root inside the runtime container and builds them with the language's own
 * compiler or type checker. The harness only wraps (a file shell, imports hoisted where the
 * language needs them at the top); it never repairs a fence, so a fence that does not declare
 * what it uses fails the build. Subclasses name the language tag, the wrapping and the build
 * commands.
 */
public abstract class AbstractFencesSpec extends AbstractIntegrationSpec {

  /** The info-string language word of the SDK, such as {@code go} or {@code typescript}. */
  protected abstract String getFenceLanguage();

  /**
   * Wraps the fences into files that the build commands compile. Every fence of the two markdown
   * files is passed, in file order (README first).
   */
  protected abstract List<HarnessFile> wrap(List<Fence> fences);

  /** The commands, run in order from the SDK root, that build the wrapped files. */
  protected abstract String[] getFenceBuildCommands();

  @Override
  protected final String[] getBuildCommands() {
    return getFenceBuildCommands();
  }

  private List<Fence> readFences() throws IOException {
    List<Fence> fences = new ArrayList<>();
    for (String name : new String[] {"README.md", "SKILLS.md"}) {
      String text = Files.readString(tempOutputDir.resolve(name), StandardCharsets.UTF_8);
      fences.addAll(FenceExtractor.extract(name, text, getFenceLanguage()));
    }
    return fences;
  }

  /** One command that writes a file, so a command stays under the argument size limit. */
  static String writeCommand(HarnessFile file) {
    String encoded =
        Base64.getEncoder().encodeToString(file.content().getBytes(StandardCharsets.UTF_8));
    String path = file.path();
    int slash = path.lastIndexOf('/');
    String mkdir = slash > 0 ? "mkdir -p '" + path.substring(0, slash) + "' && " : "";
    return mkdir + "printf '%s' '" + encoded + "' | base64 -d > '" + path + "'";
  }

  @Test
  void readmeAndSkillsFencesShouldBuild() throws IOException {
    List<Fence> fences = readFences();

    assertThat(fences)
        .withFailMessage("README.md and SKILLS.md hold no fence tagged '%s'", getFenceLanguage())
        .isNotEmpty();
    List<String> undeclared = new ArrayList<>();
    for (Fence fence : fences) {
      if (!fence.kind().equals("file") && !fence.kind().equals("statements")) {
        undeclared.add(fence.describe());
      }
    }
    assertThat(undeclared)
        .withFailMessage(
            "A fence tagged '%s' must declare its kind, file or statements, after the language:\n%s",
            getFenceLanguage(), String.join("\n", undeclared))
        .isEmpty();

    List<String> commands = new ArrayList<>();
    for (HarnessFile file : wrap(fences)) {
      commands.add(writeCommand(file));
    }
    for (String command : getFenceBuildCommands()) {
      commands.add(command);
    }
    ExecResult result = executeInRuntimeContainer(commands.toArray(new String[0]));

    assertThat(result.isSuccess())
        .withFailMessage(
            "A fence in README.md or SKILLS.md does not build ('%s' fences: %d):\n%s",
            getFenceLanguage(), fences.size(), result.output())
        .isTrue();
  }
}
