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
 * The behaviour catalogue read from {@code test-catalogue.txt}: one behaviour per line,
 * {@code areaNNN | plain-English promise}, optionally followed by {@code | multi} for a behaviour
 * that several tests intentionally cover. Blank lines and lines starting with {@code #} are
 * ignored. An empty catalogue switches the id comparison off.
 */
public final class TestCatalogue {

  /** One behaviour of the catalogue. */
  public static final class Behaviour {
    private final String id;
    private final String promise;
    private final boolean multi;

    Behaviour(String id, String promise, boolean multi) {
      this.id = id;
      this.promise = promise;
      this.multi = multi;
    }

    public String id() {
      return id;
    }

    public String promise() {
      return promise;
    }

    /** Whether several tests may carry this id in one language. */
    public boolean multi() {
      return multi;
    }
  }

  private final Map<String, Behaviour> behaviours;

  private TestCatalogue(Map<String, Behaviour> behaviours) {
    this.behaviours = Collections.unmodifiableMap(behaviours);
  }

  public static TestCatalogue empty() {
    return new TestCatalogue(new LinkedHashMap<>());
  }

  /** Reads the file; a missing file is an empty catalogue. */
  public static TestCatalogue load(Path file) throws IOException {
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
  public static TestCatalogue parse(String text) {
    Map<String, Behaviour> found = new LinkedHashMap<>();
    String[] lines = text.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      List<String> parts = new ArrayList<>();
      for (String part : line.split("\\|", -1)) {
        parts.add(part.trim());
      }
      String id = parts.get(0);
      if (!BehaviourId.CATALOGUE_ID.matcher(id).matches()) {
        throw new IllegalArgumentException(
            "catalogue line " + (i + 1) + ": '" + id + "' is not an id (lower-case letters then"
                + " exactly three digits, for example transport014)");
      }
      boolean multi = parts.size() >= 3 && "multi".equalsIgnoreCase(parts.get(parts.size() - 1));
      int end = multi ? parts.size() - 1 : parts.size();
      String promise = String.join(" | ", parts.subList(1, end)).trim();
      if (promise.isEmpty()) {
        throw new IllegalArgumentException(
            "catalogue line " + (i + 1) + ": " + id + " has no promise text");
      }
      if (found.containsKey(id)) {
        throw new IllegalArgumentException(
            "catalogue line " + (i + 1) + ": " + id + " is listed twice");
      }
      found.put(id, new Behaviour(id, promise, multi));
    }
    return new TestCatalogue(found);
  }

  public boolean isEmpty() {
    return behaviours.isEmpty();
  }

  public boolean contains(String id) {
    return behaviours.containsKey(id);
  }

  /** The behaviour with this id; check {@link #contains} first. */
  public Behaviour get(String id) {
    Behaviour found = behaviours.get(id);
    if (found == null) {
      throw new IllegalArgumentException("No behaviour " + id);
    }
    return found;
  }

  /** Every behaviour, in file order. */
  public Iterable<Behaviour> all() {
    return behaviours.values();
  }
}
