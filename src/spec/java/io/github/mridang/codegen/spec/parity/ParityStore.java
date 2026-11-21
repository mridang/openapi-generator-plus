package io.github.mridang.codegen.spec.parity;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Keeps each language's {@link ParitySummary} under {@code target/test-parity/<language>.json}
 * after its client spec ran, so the comparison spec can read them without running anything again.
 * A {@code <language>.running} marker exists while a client spec is still running, so the
 * comparison can wait for it.
 */
public final class ParityStore {

  private final Path directory;

  public ParityStore(Path directory) {
    this.directory = directory;
  }

  /** The store of the current checkout: {@code target/test-parity}. */
  public static ParityStore defaultStore() {
    return new ParityStore(Path.of("target/test-parity").toAbsolutePath());
  }

  /** The start time of this JVM; summaries older than that belong to an earlier run. */
  public static long jvmStartTime() {
    return ManagementFactory.getRuntimeMXBean().getStartTime();
  }

  public Path directory() {
    return directory;
  }

  public void markRunning(String language) throws IOException {
    Files.createDirectories(directory);
    Files.write(directory.resolve(language + ".running"), new byte[0]);
  }

  public void write(ParitySummary summary) throws IOException {
    Files.createDirectories(directory);
    Files.write(
        directory.resolve(summary.language() + ".json"),
        summary.toJson().getBytes(StandardCharsets.UTF_8));
    Files.deleteIfExists(directory.resolve(summary.language() + ".running"));
  }

  /** Clears the running marker without writing a summary, for a spec that failed early. */
  public void clearRunning(String language) throws IOException {
    Files.deleteIfExists(directory.resolve(language + ".running"));
  }

  /** The languages whose client spec has started and not yet finished. */
  public List<String> running() throws IOException {
    List<String> running = new ArrayList<>();
    if (!Files.isDirectory(directory)) {
      return running;
    }
    try (Stream<Path> files = Files.list(directory)) {
      files
          .map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(".running"))
          .forEach(n -> running.add(n.substring(0, n.length() - ".running".length())));
    }
    Collections.sort(running);
    return running;
  }

  /** Reads the summaries written at or after {@code notBefore}, by language. */
  public Map<String, ParitySummary> readSince(long notBefore) throws IOException {
    Map<String, ParitySummary> summaries = new TreeMap<>();
    if (!Files.isDirectory(directory)) {
      return summaries;
    }
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
        read(file, notBefore).ifPresent(s -> summaries.put(s.language(), s));
      }
    }
    return summaries;
  }

  private static Optional<ParitySummary> read(Path file, long notBefore) throws IOException {
    ParitySummary summary =
        ParitySummary.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    return summary.writtenAt() >= notBefore ? Optional.of(summary) : Optional.empty();
  }
}
