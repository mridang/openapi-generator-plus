package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Base class for documentation specs. Runs the generated SDK's own documentation build (the {@code
 * docs} target of its Makefile, which treats warnings as errors) inside the language's runtime
 * container, then checks that the output folder exists and is not empty, so a tool that exits
 * successfully without writing anything is caught. Subclasses only name the commands and the
 * output folder.
 */
public abstract class AbstractDocsSpec extends AbstractIntegrationSpec {

  /** The command or commands that build the documentation, run in order from the SDK root. */
  protected abstract String[] getDocsCommands();

  /** The folder, relative to the SDK root, that the documentation tool writes into. */
  protected abstract String getDocsOutputDirectory();

  @Override
  protected final String[] getBuildCommands() {
    String[] docs = getDocsCommands();
    String[] commands = new String[docs.length + 1];
    System.arraycopy(docs, 0, commands, 0, docs.length);
    String directory = getDocsOutputDirectory();
    commands[docs.length] =
        "test -n \"$(ls -A '"
            + directory
            + "' 2>/dev/null)\" || { echo \"The documentation output folder '"
            + directory
            + "' is missing or empty\"; exit 1; }";
    return commands;
  }

  @Test
  void generatedDocumentationShouldBuild() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("The documentation build failed:\n%s", result.output())
        .isTrue();
  }
}
