package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ParityIdComparisonTest {

  private static final TestCatalogue CATALOGUE =
      TestCatalogue.parse(
          "transport014 | Retries on 503\nretries007 | Backs off between attempts\n"
              + "authoauth031 | Refreshes an expired token | multi\n");

  private static final Map<String, String> STYLES =
      Map.of(
          "java", "bhvTransport014RetriesOn503",
          "kotlin", "bhvTransport014RetriesOn503",
          "csharp", "BhvTransport014RetriesOn503",
          "php", "test_bhv_transport_014_retries_on_503",
          "python", "test_bhv_transport_014_retries_on_503",
          "ruby", "test_0001_bhv transport 014 retries on 503",
          "node", "bhv transport 014 retries on 503",
          "dart", "bhv transport 014 retries on 503",
          "go", "TestBhvTransport014_RetriesOn503",
          "rust", "test_bhv_transport_014_retries_on_503");

  private static ParitySummary summary(String language, String... names) {
    return new ParitySummary(
        language, 1L, names.length, new TreeSet<>(), List.of(), Arrays.asList(names));
  }

  private static ParitySummary full(String language) {
    return summary(
        language,
        STYLES.getOrDefault(language, "testBhvTransport014RetriesOn503()"),
        "bhvRetries007BacksOff",
        "bhvAuthoauth031Refreshes");
  }

  private static Map<String, ParitySummary> present(String... languages) {
    Map<String, ParitySummary> present = new TreeMap<>();
    for (String language : languages) {
      present.put(language, full(language));
    }
    return present;
  }

  private static List<String> compare(
      Map<String, ParitySummary> present, TestCatalogueExceptions exceptions) {
    return ParityIdComparison.compare(present, CATALOGUE, exceptions);
  }

  @Test
  void shouldPassWhenEveryLanguageCoversTheCatalogue() {
    assertThat(compare(present("java", "go", "ruby", "swift"), TestCatalogueExceptions.empty()))
        .isEmpty();
  }

  @Test
  void shouldCountTheRowsOfOneParameterisedTestAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>(present("java", "go", "ruby", "swift"));
    present.put(
        "csharp",
        summary(
            "csharp",
            "Test.Api.RetriesTest.BhvTransport014RetriesOn503(status: 502, expected: typeof(Foo))",
            "Test.Api.RetriesTest.BhvTransport014RetriesOn503(status: 503, expected: typeof(Foo))",
            "Test.Api.RetriesTest.BhvRetries007BacksOffBetweenAttempts()",
            "Test.Api.AuthTest.BhvAuthoauth031RefreshesAnExpiredToken()"));
    present.put(
        "php",
        summary(
            "php",
            "bhv transport 014 retries on 503 with data set \"(['abc'], 'abc') #1\"",
            "bhv transport 014 retries on 503 with data set \"(['abc'], 'abc') #2\"",
            "bhv retries 007 backs off between attempts",
            "bhv authoauth 031 refreshes an expired token"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldCountTheRowsOfAPytestParameterisedTestAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "python",
        summary(
            "python",
            "test_bhv_transport_014_retries_on_503[a-b-c]",
            "test_bhv_transport_014_retries_on_503[d-e-f]",
            "bhvRetries007BacksOff",
            "bhvAuthoauth031Refreshes"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldCountTheRowsOfAJavaInvocationParameterisedTestAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "java",
        summary(
            "java",
            "bhvTransport014RetriesOn503(String)[1]",
            "bhvTransport014RetriesOn503(String)[2]",
            "bhvRetries007BacksOff",
            "bhvAuthoauth031Refreshes"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldCountTheRowsOfAGoSubtestParameterisedTestAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "go",
        summary(
            "go",
            "TestBhvTransport014_RetriesOn503/sub_one",
            "TestBhvTransport014_RetriesOn503/sub_two",
            "bhvRetries007BacksOff",
            "bhvAuthoauth031Refreshes"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldCountTheRowsOfARubyNumberParameterisedTestAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "ruby",
        summary(
            "ruby",
            "test_0005_bhv transport 014 retries on 503",
            "test_0006_bhv transport 014 retries on 503",
            "bhvRetries007BacksOff",
            "bhvAuthoauth031Refreshes"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldCountCsharpRowsWithUnbalancedQuotedArgumentsAsOneTest() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "csharp",
        summary(
            "csharp",
            "Test.A.BhvTransport014RetriesOn503(character: \")\")",
            "Test.A.BhvTransport014RetriesOn503(character: \"(\")",
            "Test.A.BhvRetries007BacksOff()",
            "Test.A.BhvAuthoauth031Refreshes()"));

    assertThat(compare(present, TestCatalogueExceptions.empty())).isEmpty();
  }

  @Test
  void shouldKeepRowsThatDifferAfterTheTokenAsSeparateTests() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "dart",
        summary(
            "dart",
            "bhv transport 014 retries on 503",
            "bhv transport 014 retries on 502",
            "bhv retries 007 backs off",
            "bhv authoauth 031 refreshes"));

    assertThat(compare(present, TestCatalogueExceptions.empty()))
        .singleElement()
        .asString()
        .contains("dart: duplicate ids");
  }

  @Test
  void shouldStillReportTwoDifferentTestsForTheSameBehaviour() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put(
        "csharp",
        summary(
            "csharp",
            "Test.Api.RetriesTest.BhvTransport014RetriesOn503()",
            "Test.Api.OtherTest.BhvTransport014AlsoRetriesOn503()",
            "Test.Api.RetriesTest.BhvRetries007BacksOffBetweenAttempts()",
            "Test.Api.AuthTest.BhvAuthoauth031RefreshesAnExpiredToken()"));
    present.put("java", full("java"));

    assertThat(compare(present, TestCatalogueExceptions.empty()))
        .anySatisfy(problem -> assertThat(problem).contains("duplicate ids"));
  }

  @Test
  void shouldListAMissingIdWithItsPromise() {
    Map<String, ParitySummary> present = present("java");
    present.put("go", summary("go", "TestBhvTransport014_Retries"));

    List<String> problems = compare(present, TestCatalogueExceptions.empty());

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0))
        .startsWith("go: missing behaviours")
        .contains("retries007 - Backs off between attempts")
        .contains("authoauth031 - Refreshes an expired token");
  }

  @Test
  void shouldAcceptAMissingIdThatIsExcepted() {
    Map<String, ParitySummary> present = present("java");
    present.put("go", summary("go", "TestBhvTransport014_Retries"));
    TestCatalogueExceptions exceptions =
        TestCatalogueExceptions.parse(
            "go | retries007 | no backoff hook\ngo | authoauth031 | no oauth in go");

    assertThat(compare(present, exceptions)).isEmpty();
  }

  @Test
  void shouldReportAStaleException() {
    TestCatalogueExceptions exceptions =
        TestCatalogueExceptions.parse("java | retries007 | no backoff hook");

    assertThat(compare(present("java"), exceptions))
        .singleElement()
        .asString()
        .contains("java: stale exception retries007")
        .contains("no backoff hook");
  }

  @Test
  void shouldReportAnExceptionForAnIdThatIsNotInTheCatalogue() {
    TestCatalogueExceptions exceptions = TestCatalogueExceptions.parse("java | gone001 | old");

    assertThat(compare(present("java"), exceptions))
        .singleElement()
        .asString()
        .contains("java: exception for gone001")
        .contains("not in the catalogue");
  }

  @Test
  void shouldReportAnUnknownId() {
    Map<String, ParitySummary> present = present("java");
    present.put(
        "python",
        summary(
            "python",
            "test_bhv_transport_014_a",
            "test_bhv_retries_007_b",
            "test_bhv_authoauth_031_c",
            "test_bhv_typo_999_d"));

    assertThat(compare(present, TestCatalogueExceptions.empty()))
        .singleElement()
        .asString()
        .contains("python: unknown ids")
        .contains("typo999 (test_bhv_typo_999_d)");
  }

  @Test
  void shouldReportADuplicateIdUnlessTheBehaviourIsMulti() {
    Map<String, ParitySummary> present = new TreeMap<>();
    present.put("java", full("java"));
    present.put(
        "node",
        summary(
            "node",
            "bhvTransport014A",
            "bhvTransport014B",
            "bhvRetries007C",
            "bhvAuthoauth031D",
            "bhvAuthoauth031E"));

    assertThat(compare(present, TestCatalogueExceptions.empty()))
        .singleElement()
        .asString()
        .contains("node: duplicate ids")
        .contains("transport014 in bhvTransport014A, bhvTransport014B")
        .doesNotContain("authoauth031");
  }

  @Test
  void shouldListTestsWithoutAnIdUnlessTheLanguageIsExcepted() {
    Map<String, ParitySummary> present = present("java");
    present.put(
        "rust",
        summary(
            "rust",
            "test_bhv_transport_014_a",
            "test_bhv_retries_007_b",
            "test_bhv_authoauth_031_c",
            "test_builder_is_fluent"));

    assertThat(compare(present, TestCatalogueExceptions.empty()))
        .singleElement()
        .asString()
        .contains("rust: tests without a behaviour id (1)")
        .contains("test_builder_is_fluent");
    assertThat(compare(present, TestCatalogueExceptions.parse("rust | *unnumbered | harness")))
        .isEmpty();
  }

  @Test
  void shouldReportAStaleUnnumberedException() {
    assertThat(compare(present("java"), TestCatalogueExceptions.parse("java | *unnumbered | x")))
        .singleElement()
        .asString()
        .contains("java: stale exception *unnumbered");
  }

  @Test
  void shouldFailAnUnreadableReportAndAZeroTestLanguage() {
    Map<String, ParitySummary> present = present("java");
    present.put(
        "go",
        new ParitySummary("go", 1L, 0, new TreeSet<>(), List.of("cannot read a.xml"), List.of()));

    List<String> problems = compare(present, TestCatalogueExceptions.empty());

    assertThat(problems).contains("go: cannot read a.xml", "go: the reports hold zero tests");
  }
}
