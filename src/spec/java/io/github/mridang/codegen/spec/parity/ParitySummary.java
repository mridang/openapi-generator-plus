package io.github.mridang.codegen.spec.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What one language's client spec reported: how many tests ran and the canonical key of each, so
 * a later spec can compare the languages without running them again.
 */
public final class ParitySummary {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String language;
  private final long writtenAt;
  private final int total;
  private final SortedSet<String> keys;
  private final List<String> errors;
  private final List<String> names;

  public ParitySummary(
      String language, long writtenAt, int total, SortedSet<String> keys, List<String> errors) {
    this(language, writtenAt, total, keys, errors, List.of());
  }

  public ParitySummary(
      String language,
      long writtenAt,
      int total,
      SortedSet<String> keys,
      List<String> errors,
      List<String> names) {
    this.language = language;
    this.writtenAt = writtenAt;
    this.total = total;
    this.keys = Collections.unmodifiableSortedSet(new TreeSet<>(keys));
    this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    this.names = Collections.unmodifiableList(new ArrayList<>(names));
  }

  /** The raw reported name of every test case, sorted, duplicates kept. */
  public List<String> names() {
    return names;
  }

  public String language() {
    return language;
  }

  /** Epoch milliseconds at which the report was read. */
  public long writtenAt() {
    return writtenAt;
  }

  /** The number of test cases the reports hold, before the keys are de-duplicated. */
  public int total() {
    return total;
  }

  /** The canonical {@code subject::test} keys, sorted. */
  public SortedSet<String> keys() {
    return keys;
  }

  /** Reports that could not be read, one message each. */
  public List<String> errors() {
    return errors;
  }

  /** The subjects of {@link #keys()}, sorted. */
  public SortedSet<String> groups() {
    SortedSet<String> groups = new TreeSet<>();
    for (String key : keys) {
      groups.add(key.substring(0, key.indexOf("::")));
    }
    return groups;
  }

  public String toJson() {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("language", language);
    root.put("writtenAt", writtenAt);
    root.put("total", total);
    ArrayNode errorNodes = root.putArray("errors");
    errors.forEach(errorNodes::add);
    ArrayNode nameNodes = root.putArray("names");
    names.forEach(nameNodes::add);
    ArrayNode keyNodes = root.putArray("keys");
    keys.forEach(keyNodes::add);
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static ParitySummary fromJson(String json) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    SortedSet<String> keys = new TreeSet<>();
    root.path("keys").forEach(node -> keys.add(node.asText()));
    List<String> errors = new ArrayList<>();
    root.path("errors").forEach(node -> errors.add(node.asText()));
    List<String> names = new ArrayList<>();
    root.path("names").forEach(node -> names.add(node.asText()));
    return new ParitySummary(
        root.path("language").asText(),
        root.path("writtenAt").asLong(),
        root.path("total").asInt(),
        keys,
        errors,
        names);
  }
}
