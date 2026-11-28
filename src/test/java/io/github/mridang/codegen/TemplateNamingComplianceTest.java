package io.github.mridang.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Compliance test that verifies all custom template filenames use lowercase snake_case naming.
 */
public class TemplateNamingComplianceTest {

  private static final Path TEMPLATES_DIR = Paths.get("src/main/resources/templates");
  private static final String[] LANGUAGES = {"java", "node", "php", "python", "ruby"};

  @Test
  void allTemplateFilenamesMustBeSnakeCase() throws IOException {
    for (String lang : LANGUAGES) {
      Path langDir = TEMPLATES_DIR.resolve(lang);
      assertTrue(Files.isDirectory(langDir), "Template directory must exist: " + langDir);

      try (Stream<Path> files = Files.list(langDir)) {
        files
            .filter(p -> p.getFileName().toString().endsWith(".mustache"))
            .forEach(
                p -> {
                  String name = p.getFileName().toString();
                  assertTrue(
                      name.matches("[a-z_][a-z0-9_]*\\.mustache"),
                      lang + "/" + name + " must be lowercase snake_case");
                });
      }
    }
  }
}
