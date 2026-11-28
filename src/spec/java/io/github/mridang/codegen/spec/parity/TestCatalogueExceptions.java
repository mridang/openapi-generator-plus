package io.github.mridang.codegen.spec.parity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-language exceptions read from {@code test-catalogue-exceptions.txt}: lines
 * {@code language | areaNNN | specific reason}. The id {@code *unnumbered} allows that language
 * to have tests without an id. Blank lines and lines starting with {@code #} are ignored.
 */
public final class TestCatalogueExceptions {

  /** The id that allows a language to carry tests without a behaviour id. */
  public static final String UNNUMBERED = "*unnumbered";

  /** The twelve language ids a line may name. */
  public static final List<String> LANGUAGES =
      List.of(
          "java", "kotlin", "csharp", "php", "python", "ruby", "node", "dart", "go", "rust",
          "swift", "elixir");

  private final Map<String, Map<String, String>> reasons;

  private TestCatalogueExceptions(Map<String, Map<String, String>> reasons) {
    this.reasons = Collections.unmodifiableMap(reasons);
  }

  public static TestCatalogueExceptions empty() {
    return new TestCatalogueExceptions(new LinkedHashMap<>());
  }

  /** Reads the file; a missing file has no exceptions. */
  public static TestCatalogueExceptions load(Path file) throws IOException {
    if (!Files.exists(file)) {
      return empty();
    }
    return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
  }

  /**
   * Parses the text.
   *
   * @throws IllegalArgumentException naming the line of the first malformed entry
   */
  public static TestCatalogueExceptions parse(String text) {
    Map<String, Map<String, String>> found = new LinkedHashMap<>();
    String[] lines = text.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split("\\|", 3);
      String where = "exceptions line " + (i + 1) + ": ";
      if (parts.length < 3 || parts[2].trim().isEmpty()) {
        throw new IllegalArgumentException(
            where + "expected 'language | areaNNN | reason' with a reason");
      }
      String language = parts[0].trim();
      String id = parts[1].trim();
      if (!LANGUAGES.contains(language)) {
        throw new IllegalArgumentException(where + "unknown language '" + language + "'");
      }
      if (!UNNUMBERED.equals(id) && !BehaviourId.CATALOGUE_ID.matcher(id).matches()) {
        throw new IllegalArgumentException(
            where + "'" + id + "' is not an id (or " + UNNUMBERED + ")");
      }
      if (found.computeIfAbsent(language, k -> new LinkedHashMap<>())
              .putIfAbsent(id, parts[2].trim())
          != null) {
        throw new IllegalArgumentException(where + language + " lists " + id + " twice");
      }
    }
    return new TestCatalogueExceptions(found);
  }

  /** Whether the language is excused from this id. */
  public boolean allows(String language, String id) {
    return reasons.getOrDefault(language, Map.of()).containsKey(id);
  }

  /** The excepted ids of a language, in file order (including {@code *unnumbered}). */
  public List<String> idsOf(String language) {
    return new ArrayList<>(reasons.getOrDefault(language, Map.of()).keySet());
  }

  /** The reason of an exception that {@link #allows} confirmed. */
  public String reason(String language, String id) {
    String found = reasons.getOrDefault(language, Map.of()).get(id);
    if (found == null) {
      throw new IllegalArgumentException("No exception " + language + " " + id);
    }
    return found;
  }
}
