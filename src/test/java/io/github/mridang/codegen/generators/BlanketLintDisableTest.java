package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;

/**
 * Guardrail G5 (F1172): a generated file may not switch off a whole tool or rule set. A named
 * single-rule suppression is allowed (a shipped config says why); a blanket one hides every
 * finding in the file from the linters, which is how a {@code detekt:all} once hid 76 files of
 * real findings. The committed goldens are the generated output (the gate fails when they
 * differ), so scanning them covers every template.
 */
class BlanketLintDisableTest {

  private static final Path GOLDENS = Path.of("src", "spec", "resources", "generated");

  /** Source file extensions; config, manifest and prose files only describe the rule. */
  private static final Set<String> SOURCE_EXTENSIONS =
      Set.of(
          ".java", ".kt", ".cs", ".php", ".py", ".rb", ".ts", ".js", ".mjs", ".dart", ".go",
          ".rs", ".swift", ".ex", ".exs");

  private static final List<Pattern> BLANKET =
      List.of(
          Pattern.compile("rubocop:(disable|todo)\\s+all\\b"),
          Pattern.compile("rubocop:(disable|todo)\\s*$"),
          Pattern.compile("detekt:all"),
          Pattern.compile("@file:Suppress\\(\\s*\"(all|detekt[^\"]*)\""),
          Pattern.compile("@(file:)?Suppress(Warnings)?\\(\\s*\"all\""),
          Pattern.compile("#\\s*noqa(?!\\s*:\\s*[A-Z]+[0-9]+)"),
          Pattern.compile("(ruff|flake8):\\s*noqa(?!\\s*:\\s*[A-Z]+[0-9]+)"),
          Pattern.compile("#\\s*type:\\s*ignore(?!\\[)"),
          Pattern.compile("pylint:\\s*disable\\s*=\\s*all"),
          Pattern.compile("phpcs:(ignoreFile|disable)\\s*$"),
          Pattern.compile("phpcs:ignoreFile"),
          Pattern.compile("@phpstan-ignore-file"),
          Pattern.compile("eslint-disable(-next-line|-line)?\\s*(\\*/)?\\s*$"),
          Pattern.compile("@ts-nocheck"),
          Pattern.compile("swiftlint:(disable|ignore)\\s+all\\b"),
          Pattern.compile("swiftlint:disable\\s*$"),
          Pattern.compile("//\\s*nolint\\s*($|:\\s*(all)?\\s*$)"),
          Pattern.compile("nolint:all"),
          Pattern.compile("ignore_for_file:\\s*type\\s*=\\s*lint"),
          Pattern.compile("#!?\\[(allow|expect)\\((warnings|clippy::all|unused)\\)\\]"),
          Pattern.compile("#pragma\\s+warning\\s+disable\\s*$"),
          Pattern.compile("credo:disable-for-this-file\\s*$"),
          Pattern.compile("checkstyle:off\\s*$"),
          Pattern.compile("CHECKSTYLE:OFF\\s*$"),
          Pattern.compile("NOSONAR\\s*$"));

  static List<String> blanketDisables(String line) {
    final List<String> hits = new ArrayList<>();
    for (final Pattern p : BLANKET) {
      if (p.matcher(line).find()) {
        hits.add(p.pattern());
      }
    }
    return hits;
  }

  private static boolean isSource(Path file) {
    final String name = file.getFileName().toString();
    if (name.startsWith(".")) {
      return false;
    }
    for (final String ext : SOURCE_EXTENSIONS) {
      if (name.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  @Test
  /* A generator test rewrites its golden while this walks it, so read every golden under its lock. */
  @ResourceLocks({
    @ResourceLock(value = "generated-csharp", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-dart", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-go", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-java", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-kotlin", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-node", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-php", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-ruby", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-rust", mode = ResourceAccessMode.READ),
    @ResourceLock(value = "generated-swift", mode = ResourceAccessMode.READ)
  })
  @DisplayName("no committed golden disables a whole tool or rule set")
  void noGoldenCarriesABlanketDisable() throws IOException {
    final List<String> violations = new ArrayList<>();
    try (Stream<Path> files = Files.walk(GOLDENS)) {
      for (final Path file :
          files
              .filter(Files::isRegularFile)
              .filter(BlanketLintDisableTest::isSource)
              .filter(f -> !f.toString().contains("node_modules"))
              .collect(Collectors.toList())) {
        final List<String> lines;
        try {
          lines = Files.readAllLines(file);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
          if (!blanketDisables(lines.get(i)).isEmpty()) {
            violations.add(GOLDENS.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
          }
        }
      }
    }
    assertEquals(
        List.of(),
        violations,
        "Generated files may not disable a whole tool or rule set (G5); name the single rule in"
            + " the shipped config instead");
  }

  @Test
  @DisplayName("every blanket directive form is recognised")
  void blanketFormsAreRecognised() {
    for (final String line :
        List.of(
            "# rubocop:disable all",
            "@file:Suppress(\"detekt:all\")",
            "x = 1  # noqa",
            "# ruff: noqa",
            "x = 1  # type: ignore",
            "// phpcs:ignoreFile",
            "/* eslint-disable */",
            "// eslint-disable-next-line",
            "// @ts-nocheck",
            "// swiftlint:disable all",
            "x := 1 // nolint",
            "// ignore_for_file: type=lint",
            "#![allow(warnings)]",
            "#pragma warning disable",
            "@SuppressWarnings(\"all\")")) {
      assertTrue(!blanketDisables(line).isEmpty(), line);
    }
  }

  @Test
  @DisplayName("a suppression that names its one rule is allowed")
  void namedSuppressionsAreAllowed() {
    for (final String line :
        List.of(
            "x = 1  # type: ignore[return-value]",
            "x = 1  # noqa: E501",
            "# rubocop:disable Metrics/MethodLength",
            "/* eslint-disable no-console */",
            "// eslint-disable-next-line no-console",
            "x := 1 // nolint:errcheck",
            "@SuppressWarnings(\"unchecked\")",
            "@Suppress(\"UNCHECKED_CAST\")",
            "#pragma warning disable CA2016",
            "// swiftlint:disable force_cast")) {
      assertFalse(!blanketDisables(line).isEmpty(), line);
    }
  }
}
