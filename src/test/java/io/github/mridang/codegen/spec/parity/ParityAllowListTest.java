package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mridang.codegen.spec.parity.ParityAllowList.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParityAllowListTest {

  private static final String JSON =
      "{\"languages\":{\"go\":{\"missingGroups\":[{\"reason\":\"tracked in #118\","
          + "\"keys\":[\"objectserializer\",\"oauth2*\"]}],\"extraGroups\":[{\"reason\":\"idiom\","
          + "\"keys\":[\"*\"]}]}}}";

  @Test
  void shouldAllowAnExactKeyAndAPrefix() throws IOException {
    ParityAllowList allow = ParityAllowList.parse(JSON);

    assertThat(allow.allows("go", Kind.MISSING_GROUPS, "objectserializer")).isTrue();
    assertThat(allow.allows("go", Kind.MISSING_GROUPS, "oauth2tokenmanager")).isTrue();
    assertThat(allow.allows("go", Kind.MISSING_GROUPS, "objectserializer2")).isFalse();
  }

  @Test
  void shouldAllowEveryKeyOfAKindWithAStar() throws IOException {
    assertThat(ParityAllowList.parse(JSON).allows("go", Kind.EXTRA_GROUPS, "anything")).isTrue();
  }

  @Test
  void shouldKeepLanguagesAndKindsApart() throws IOException {
    ParityAllowList allow = ParityAllowList.parse(JSON);

    assertThat(allow.allows("ruby", Kind.MISSING_GROUPS, "objectserializer")).isFalse();
    assertThat(allow.allows("go", Kind.MISSING_TESTS, "objectserializer")).isFalse();
    assertThat(allow.languages()).containsExactly("go");
  }

  @Test
  void shouldAllowNothingWhenEmpty() {
    assertThat(ParityAllowList.empty().allows("go", Kind.MISSING_GROUPS, "x")).isFalse();
    assertThat(ParityAllowList.empty().entries("go", Kind.MISSING_GROUPS)).isEmpty();
  }

  @Test
  void shouldKeepTheReasonOfEveryEntry() throws IOException {
    ParityAllowList.Entry entry = ParityAllowList.parse(JSON).entries("go", Kind.MISSING_GROUPS).get(0);

    assertThat(entry.reason()).isEqualTo("tracked in #118");
    assertThat(entry.keys()).containsExactly("objectserializer", "oauth2*");
  }

  @Test
  void shouldReadThePartialGroupsOfTheFamily() throws IOException {
    ParityAllowList allow =
        ParityAllowList.parse(
            "{\"partialGroups\":[{\"reason\":\"r\",\"keys\":[\"skills\",\"scheme*\"]}]}");

    assertThat(allow.allowsPartialGroup("skills")).isTrue();
    assertThat(allow.allowsPartialGroup("schemeauthenticators")).isTrue();
    assertThat(allow.allowsPartialGroup("readme")).isFalse();
    assertThat(allow.partialGroups()).hasSize(1);
    assertThat(ParityAllowList.empty().allowsPartialGroup("skills")).isFalse();
  }

  @Test
  void shouldRefuseAnEntryWithoutAReason() {
    assertThatThrownBy(
            () ->
                ParityAllowList.parse(
                    "{\"languages\":{\"go\":{\"missingTests\":[{\"keys\":[\"a::b\"]}]}}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no reason");
  }

  @Test
  void shouldRefuseAnEntryWithoutKeys() {
    assertThatThrownBy(
            () ->
                ParityAllowList.parse(
                    "{\"languages\":{\"go\":{\"missingTests\":[{\"reason\":\"r\"}]}}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no keys");
  }

  @Test
  void shouldRefuseAnUnknownKind() {
    assertThatThrownBy(
            () ->
                ParityAllowList.parse(
                    "{\"languages\":{\"go\":{\"missingStuff\":[{\"reason\":\"r\",\"keys\":[\"a\"]}]}}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missingStuff");
  }

  @Test
  void shouldLoadFromAFile(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("allow.json");
    Files.write(file, JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThat(ParityAllowList.load(file).languages()).containsExactly("go");
  }

  @Test
  void shouldLoadTheCommittedAllowFileWithAReasonOnEveryEntry() throws IOException {
    ParityAllowList allow = ParityAllowList.load(Path.of("src/spec/resources/test-parity-allow.json"));

    assertThat(allow.languages()).isNotEmpty();
    for (String language : allow.languages()) {
      for (Kind kind : Kind.values()) {
        allow.entries(language, kind).forEach(e -> assertThat(e.reason()).isNotBlank());
      }
    }
  }
}
