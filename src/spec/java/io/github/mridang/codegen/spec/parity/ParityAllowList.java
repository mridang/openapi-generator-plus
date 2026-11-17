package io.github.mridang.codegen.spec.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The known gaps between the languages' test suites, read from {@code test-parity-allow.json}.
 * Every entry names the language, the kind of gap, the keys it covers and a reason, so a gap is
 * always a decision. A key ending in {@code *} covers every key with that prefix, and {@code *}
 * alone covers every key of the kind. The top-level {@code partialGroups} entries list the
 * groups that only some of the languages have.
 */
public final class ParityAllowList {

  /** The kinds of gap an entry can allow, named as in the JSON file. */
  public enum Kind {
    MISSING_GROUPS("missingGroups"),
    EXTRA_GROUPS("extraGroups"),
    MISSING_TESTS("missingTests");

    private final String jsonName;

    Kind(String jsonName) {
      this.jsonName = jsonName;
    }

    public String jsonName() {
      return jsonName;
    }
  }

  /** One entry of the file: the keys it covers and why. */
  public static final class Entry {
    private final String reason;
    private final List<String> keys;

    Entry(String reason, List<String> keys) {
      this.reason = reason;
      this.keys = Collections.unmodifiableList(keys);
    }

    public String reason() {
      return reason;
    }

    public List<String> keys() {
      return keys;
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Map<String, Map<Kind, List<Entry>>> entries;
  private final List<Entry> partialGroups;

  private ParityAllowList(Map<String, Map<Kind, List<Entry>>> entries, List<Entry> partialGroups) {
    this.entries = entries;
    this.partialGroups = partialGroups;
  }

  public static ParityAllowList empty() {
    return new ParityAllowList(new HashMap<>(), List.of());
  }

  public static ParityAllowList load(Path file) throws IOException {
    return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
  }

  /**
   * Parses the JSON text.
   *
   * @throws IllegalArgumentException when an entry has no reason, no keys or an unknown kind
   */
  public static ParityAllowList parse(String json) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    JsonNode languages = root.path("languages");
    List<Entry> partial = new ArrayList<>();
    for (JsonNode entry : root.path("partialGroups")) {
      partial.add(entry("(any)", "partialGroups", entry));
    }
    Map<String, Map<Kind, List<Entry>>> entries = new HashMap<>();
    languages
        .fieldNames()
        .forEachRemaining(
            language -> {
              Map<Kind, List<Entry>> byKind = new EnumMap<>(Kind.class);
              JsonNode node = languages.get(language);
              node.fieldNames()
                  .forEachRemaining(
                      kindName -> {
                        Kind kind = kindOf(language, kindName);
                        List<Entry> list = new ArrayList<>();
                        for (JsonNode entry : node.get(kindName)) {
                          list.add(entry(language, kindName, entry));
                        }
                        byKind.put(kind, list);
                      });
              entries.put(language, byKind);
            });
    return new ParityAllowList(entries, partial);
  }

  private static Kind kindOf(String language, String name) {
    for (Kind kind : Kind.values()) {
      if (kind.jsonName().equals(name)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown kind '" + name + "' for " + language);
  }

  private static Entry entry(String language, String kind, JsonNode node) {
    String reason = node.path("reason").asText("").trim();
    if (reason.isEmpty()) {
      throw new IllegalArgumentException(
          "An allow entry of " + language + "/" + kind + " has no reason: " + node);
    }
    List<String> keys = new ArrayList<>();
    node.path("keys").forEach(key -> keys.add(key.asText()));
    if (keys.isEmpty()) {
      throw new IllegalArgumentException(
          "An allow entry of " + language + "/" + kind + " has no keys: " + node);
    }
    return new Entry(reason, keys);
  }

  public List<Entry> entries(String language, Kind kind) {
    return entries.getOrDefault(language, Collections.emptyMap()).getOrDefault(kind, List.of());
  }

  /** Whether an entry of {@code language} and {@code kind} covers {@code key}. */
  public boolean allows(String language, Kind kind, String key) {
    for (Entry entry : entries(language, kind)) {
      for (String pattern : entry.keys()) {
        if (matches(pattern, key)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether {@code pattern} (a key, or a prefix ending in {@code *}) covers {@code key}. */
  static boolean matches(String pattern, String key) {
    if (pattern.endsWith("*")) {
      return key.startsWith(pattern.substring(0, pattern.length() - 1));
    }
    return pattern.equals(key);
  }

  /**
   * The groups that only some languages have, listed once for the whole family. They are not a
   * gap of one language, but a run of a few languages can hold only one side of them.
   */
  public List<Entry> partialGroups() {
    return partialGroups;
  }

  /** Whether {@code group} is listed as one that only some languages have. */
  public boolean allowsPartialGroup(String group) {
    for (Entry entry : partialGroups) {
      for (String pattern : entry.keys()) {
        if (matches(pattern, group)) {
          return true;
        }
      }
    }
    return false;
  }

  public List<String> languages() {
    List<String> languages = new ArrayList<>(entries.keySet());
    Collections.sort(languages);
    return languages;
  }
}
