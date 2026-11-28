package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ParityComparisonTest {

  private static final List<String> FAMILY =
      List.of(
          "java", "kotlin", "csharp", "php", "python", "ruby", "node", "dart", "go", "rust",
          "swift", "elixir");

  private static ParitySummary summary(String language, String... keys) {
    return new ParitySummary(
        language, 1L, keys.length, new TreeSet<>(Arrays.asList(keys)), List.of());
  }

  private static Map<String, ParitySummary> family(String... keys) {
    Map<String, ParitySummary> present = new TreeMap<>();
    FAMILY.forEach(language -> present.put(language, summary(language, keys)));
    return present;
  }

  @Test
  void shouldNeedTwoThirdsOfTheLanguagesAndNeverFewerThanTwo() {
    assertThat(ParityComparison.quorum(12)).isEqualTo(8);
    assertThat(ParityComparison.quorum(3)).isEqualTo(2);
    assertThat(ParityComparison.quorum(2)).isEqualTo(2);
    assertThat(ParityComparison.quorum(1)).isEqualTo(2);
  }

  @Test
  void shouldPassWhenEveryLanguageHasTheSameTests() {
    ParityComparison.Result result =
        ParityComparison.compare(family("a::one", "b::two"), ParityAllowList.empty());

    assertThat(result.problems()).isEmpty();
  }

  @Test
  void shouldNameTheMissingGroupAndItsTestsOfTheLanguageThatLacksThem() {
    Map<String, ParitySummary> present = family("a::one", "b::two");
    present.put("go", summary("go", "a::one"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).containsExactly("go: missing groups (1): b");
    assertThat(result.gapsOf("go").missingTests()).isEmpty();
  }

  @Test
  void shouldNameTheExtraGroupOfTheLanguageThatHasIt() throws IOException {
    Map<String, ParitySummary> present = family("a::one");
    present.put("ruby", summary("ruby", "a::one", "gem::manifest"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).containsExactly("ruby: extra groups (1): gem");
    assertThat(result.gapsOf("ruby").extraTests()).containsExactly("gem::manifest");
  }

  @Test
  void shouldFailOnAMissingTestOfACommonGroupWhenAllTwelveRan() {
    Map<String, ParitySummary> present = family("a::one", "a::two");
    present.put("dart", summary("dart", "a::one"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).containsExactly("dart: missing tests (1): a::two");
  }

  @Test
  void shouldNotFailOnATestOnlyOneLanguageHas() {
    Map<String, ParitySummary> present = family("a::one");
    present.put("dart", summary("dart", "a::one", "a::dartonly"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).isEmpty();
    assertThat(result.gapsOf("dart").extraTests()).containsExactly("a::dartonly");
  }

  @Test
  void shouldFailWhenALanguageReportsZeroTests() {
    Map<String, ParitySummary> present = family("a::one");
    present.put("go", new ParitySummary("go", 1L, 0, new TreeSet<>(), List.of()));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).contains("go: the reports hold zero tests");
  }

  @Test
  void shouldFailWhenAReportCouldNotBeRead() {
    Map<String, ParitySummary> present = family("a::one");
    present.put(
        "dart",
        new ParitySummary(
            "dart", 1L, 1, new TreeSet<>(List.of("a::one")), List.of("cannot read junit.xml: x")));

    assertThat(ParityComparison.compare(present, ParityAllowList.empty()).problems())
        .containsExactly("dart: cannot read junit.xml: x");
  }

  @Test
  void shouldAcceptAGapTheAllowFileListsForThatLanguage() throws IOException {
    Map<String, ParitySummary> present = family("a::one", "a::two", "b::three");
    present.put("go", summary("go", "a::one"));
    ParityAllowList allow =
        ParityAllowList.parse(
            "{\"languages\":{\"go\":{\"missingGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}],"
                + "\"missingTests\":[{\"reason\":\"r\",\"keys\":[\"a::two\"]}]}}}");

    assertThat(ParityComparison.compare(present, allow).problems()).isEmpty();
  }

  @Test
  void shouldFailOnAnAllowEntryThatCoversNoGroupGapAnyMoreWhenAllTwelveRan() throws IOException {
    ParityAllowList allow =
        ParityAllowList.parse(
            "{\"languages\":{\"go\":{\"missingGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}]}}}");

    ParityComparison.Result result = ParityComparison.compare(family("a::one"), allow);

    assertThat(result.problems()).hasSize(1);
    assertThat(result.problems().get(0))
        .contains("go: the allow entry missingGroups 'b' covers no gap any more");
  }

  @Test
  void shouldFailOnAnAllowEntryForALanguageThatHasNoReportsWhenAllTwelveRan() throws IOException {
    ParityAllowList allow =
        ParityAllowList.parse(
            "{\"languages\":{\"cobol\":{\"missingGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}]}}}");

    assertThat(ParityComparison.compare(family("a::one"), allow).problems())
        .containsExactly("cobol: the allow file names a language that has no reports");
  }

  @Test
  void shouldCompareOnlyTheLanguagesPresentAndNeverCallAnAllowEntryStale() throws IOException {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put("go", summary("go", "a::one"));
    present.put("ruby", summary("ruby", "a::one"));
    ParityAllowList allow =
        ParityAllowList.parse(
            "{\"languages\":{\"java\":{\"missingGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}]}}}");

    assertThat(ParityComparison.compare(present, allow).problems()).isEmpty();
  }

  @Test
  void shouldJudgeAPairOfLanguagesByTheAllowFileOfEitherSide() throws IOException {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put("go", summary("go", "a::one"));
    present.put("ruby", summary("ruby", "a::one", "b::two"));
    ParityAllowList lacker =
        ParityAllowList.parse(
            "{\"languages\":{\"go\":{\"missingGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}]}}}");

    assertThat(ParityComparison.compare(present, ParityAllowList.empty()).problems())
        .containsExactly("ruby: extra groups (1): b");
    assertThat(ParityComparison.compare(present, lacker).problems()).isEmpty();

    Map<String, ParitySummary> reverse = new TreeMap<>();
    reverse.put("go", summary("go", "a::one", "b::two"));
    reverse.put("ruby", summary("ruby", "a::one"));
    ParityAllowList holder =
        ParityAllowList.parse(
            "{\"languages\":{\"go\":{\"extraGroups\":[{\"reason\":\"r\",\"keys\":[\"b\"]}]}}}");

    assertThat(ParityComparison.compare(reverse, ParityAllowList.empty()).problems())
        .containsExactly("go: extra groups (1): b");
    assertThat(ParityComparison.compare(reverse, holder).problems()).isEmpty();
  }

  @Test
  void shouldNotJudgeTestsOfASubsetOfTheLanguages() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put("go", summary("go", "a::one"));
    present.put("ruby", summary("ruby", "a::one", "a::two"));
    present.put("node", summary("node", "a::one", "a::two"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.problems()).isEmpty();
    assertThat(result.gapsOf("go").missingTests()).containsExactly("a::two");
  }

  @Test
  void shouldFailOnAPartialGroupThatIsNotListedWhenAllTwelveRan() throws IOException {
    Map<String, ParitySummary> present = family("a::one");
    present.put("go", summary("go", "a::one", "skills::one"));
    present.put("ruby", summary("ruby", "a::one", "skills::one"));

    ParityComparison.Result result = ParityComparison.compare(present, ParityAllowList.empty());

    assertThat(result.partialGroups()).containsExactly("skills");
    assertThat(result.problems()).hasSize(1);
    assertThat(result.problems().get(0)).contains("partial groups (1").contains("skills");

    ParityAllowList listed =
        ParityAllowList.parse(
            "{\"partialGroups\":[{\"reason\":\"r\",\"keys\":[\"skills\"]}],\"languages\":{}}");
    assertThat(ParityComparison.compare(present, listed).problems()).isEmpty();
  }

  @Test
  void shouldFailOnAPartialGroupEntryThatIsNotPartialAnyMoreWhenAllTwelveRan() throws IOException {
    ParityAllowList listed =
        ParityAllowList.parse(
            "{\"partialGroups\":[{\"reason\":\"r\",\"keys\":[\"skills\"]}],\"languages\":{}}");

    assertThat(ParityComparison.compare(family("a::one"), listed).problems())
        .containsExactly("the allow entry partialGroups 'skills' is not a partial group any more, remove it");
  }

  @Test
  void shouldNotBlameARunOfAFewLanguagesForAGroupOnlySomeLanguagesHave() throws IOException {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put("go", summary("go", "a::one", "skills::one"));
    present.put("ruby", summary("ruby", "a::one"));
    ParityAllowList listed =
        ParityAllowList.parse(
            "{\"partialGroups\":[{\"reason\":\"r\",\"keys\":[\"skills\"]}],\"languages\":{}}");

    assertThat(ParityComparison.compare(present, ParityAllowList.empty()).problems())
        .containsExactly("go: extra groups (1): skills");
    assertThat(ParityComparison.compare(present, listed).problems()).isEmpty();
  }

  @Test
  void shouldDescribeTheGapsOfEveryLanguageForTheLog() {
    Map<String, ParitySummary> present = family("a::one");
    present.put("dart", summary("dart", "a::one", "a::dartonly"));

    List<String> lines =
        new ArrayList<>(
            ParityComparison.describe(
                ParityComparison.compare(present, ParityAllowList.empty()).gaps()));

    assertThat(lines).hasSize(12);
    assertThat(lines)
        .contains(
            "dart: 0 missing groups, 0 extra groups, 0 missing tests, 1 tests no other language"
                + " has, for example a::dartonly");
  }
}
