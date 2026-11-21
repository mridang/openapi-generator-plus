package io.github.mridang.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Secret scanners flag committed private keys; the tests generate their TLS material instead. */
class NoCommittedPrivateKeysTest {

  private static final List<String> MARKERS =
      List.of("BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY", "BEGIN EC PRIVATE KEY");

  private static List<String> offenders(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(
              file -> {
                try {
                  String text = Files.readString(file, StandardCharsets.ISO_8859_1);
                  return MARKERS.stream().anyMatch(text::contains);
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .map(Path::toString)
          .collect(Collectors.toList());
    }
  }

  @Test
  @DisplayName("no file under src/main or src/spec/resources holds a private key")
  void noPrivateKeyIsCommitted() throws IOException {
    List<String> found = new java.util.ArrayList<>(offenders(Path.of("src/main")));
    found.addAll(offenders(Path.of("src/spec/resources")));
    assertTrue(found.isEmpty(), "committed private keys: " + found);
  }
}
