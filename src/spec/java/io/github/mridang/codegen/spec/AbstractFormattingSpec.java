package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;


/**
 * Base class for formatting specs. Provides shared tests for HTML entities and inline comments.
 * Subclasses must implement the formatting test itself since tool setup varies per language.
 */
public abstract class AbstractFormattingSpec extends AbstractIntegrationSpec {

  protected abstract String getFileExtension();

  /**
   * Return the root directory containing generated source code. Only files under this path
   * will be scanned for HTML entities and inline comments. Override in each language spec
   * to point at the generated source directory (e.g. "lib", "src/main", "petstore_client").
   */
  protected Path getSourceRoot() {
    return tempOutputDir;
  }

  /**
   * Return the regex pattern for detecting inline comments, or null to skip the test. Default
   * matches C-style inline comments. Override to return null for languages that use hash comments
   * (Python, Ruby). Override to return a custom pattern for C# to exclude XML doc comments.
   */
  @Nullable
  protected String getInlineCommentPattern() {
    return "^\\s*//";
  }

  /**
   * Return true if this file should be checked for inline comments.
   * Override to add additional exclusions beyond the file extension filter.
   */
  protected boolean includeFileForInlineCommentCheck(Path file) {
    return true;
  }

  /**
   * Return true to skip leading file-header comment lines when checking for inline comments.
   * When enabled, contiguous comment or blank lines at the start of each file are ignored.
   */
  protected boolean skipFileHeaderComments() {
    return false;
  }

  @Test
  void generatedCodeShouldNotContainHtmlEntities() throws IOException {
    Pattern htmlEntity = Pattern.compile("&(lt|gt|amp|quot);");
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(getSourceRoot())) {
      files
          .filter(p -> p.toString().endsWith(getFileExtension()))
          .forEach(
              p -> {
                try {
                  List<String> lines = Files.readAllLines(p);
                  for (int i = 0; i < lines.size(); i++) {
                    if (htmlEntity.matcher(lines.get(i)).find()) {
                      violations.add(
                          p.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }

    assertThat(violations)
        .withFailMessage(
            "Found HTML-encoded entities in generated code:\n%s", String.join("\n", violations))
        .isEmpty();
  }

  @Test
  void generatedCodeShouldNotContainInlineComments() throws IOException {
    String commentPattern = getInlineCommentPattern();
    if (commentPattern == null) {
      return;
    }

    Pattern inlineComment = Pattern.compile(commentPattern);
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(getSourceRoot())) {
      files
          .filter(p -> p.toString().endsWith(getFileExtension()))
          .filter(this::includeFileForInlineCommentCheck)
          .forEach(
              p -> {
                try {
                  List<String> lines = Files.readAllLines(p);
                  int start = 0;
                  if (skipFileHeaderComments()) {
                    while (start < lines.size()) {
                      String line = lines.get(start).trim();
                      if (line.isEmpty() || inlineComment.matcher(lines.get(start)).find()) {
                        start++;
                      } else {
                        break;
                      }
                    }
                  }
                  for (int i = start; i < lines.size(); i++) {
                    if (inlineComment.matcher(lines.get(i)).find()) {
                      violations.add(
                          p.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }

    assertThat(violations)
        .withFailMessage(
            "Found inline comments in generated code:\n%s", String.join("\n", violations))
        .isEmpty();
  }
}
