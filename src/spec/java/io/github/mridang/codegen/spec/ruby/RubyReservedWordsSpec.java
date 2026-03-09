package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class RubyReservedWordsSpec {

  @Test
  void reservedWordsShouldContainAllRuntimeKeywords() throws Exception {
    try (GenericContainer<?> container =
        new GenericContainer<>("ruby:3.4-slim").withCommand("tail", "-f", "/dev/null")) {
      container.start();
      container.copyFileToContainer(
          MountableFile.forClasspathResource("scripts/dump-ruby-keywords.sh"),
          "/scripts/dump.sh");
      container.copyFileToContainer(
          MountableFile.forClasspathResource("scripts/ruby_keywords.rb"),
          "/scripts/ruby_keywords.rb");

      Container.ExecResult result = container.execInContainer("sh", "/scripts/dump.sh");
      assertThat(result.getExitCode())
          .as("Script stderr: %s", result.getStderr())
          .isZero();

      Set<String> runtimeKeywords = parseLines(result.getStdout());
      Set<String> resourceKeywords = loadResourceFile("/reserved-words/ruby.txt");

      assertThat(resourceKeywords)
          .as("Resource file should contain all runtime keywords")
          .containsAll(runtimeKeywords);
    }
  }

  private static Set<String> parseLines(String output) {
    return Arrays.stream(output.split("\n"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.toSet());
  }

  private static Set<String> loadResourceFile(String resourcePath) throws IOException {
    try (InputStream is = AbstractBetterCodegen.class.getResourceAsStream(resourcePath)) {
      assertThat(is).as("Resource file %s should exist", resourcePath).isNotNull();
      return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.toSet());
    }
  }
}
