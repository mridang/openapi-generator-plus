package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class ParityReportParserTest {

  @TempDir Path reports;

  private void write(String name, String xml) throws IOException {
    Files.write(reports.resolve(name), xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldReadAPerClassSurefireReport() throws IOException {
    write(
        "TEST-a.xml",
        "<testsuite name='com.example.petstore.ConfigurationTest' tests='2'>"
            + "<testcase classname='com.example.petstore.ConfigurationTest' name='builderIsFluent'/>"
            + "<testcase classname='com.example.petstore.ConfigurationTest' name='invalidEnumValueThrows'/>"
            + "</testsuite>");

    ParitySummary summary = ParityReportParser.parse("java", reports, 5L);

    assertThat(summary.total()).isEqualTo(2);
    assertThat(summary.writtenAt()).isEqualTo(5L);
    assertThat(summary.keys())
        .containsExactly("configuration::builderisfluent", "configuration::invalidenumvaluethrows");
    assertThat(summary.errors()).isEmpty();
    assertThat(summary.names()).containsExactly("builderIsFluent", "invalidEnumValueThrows");
  }

  @Test
  void shouldReadEverySuiteOfACombinedReportAndKeepCasesOfSuitesThatHoldSuites() throws IOException {
    write(
        "junit.xml",
        "<testsuites><testsuite name=''>"
            + "<testsuite name='Tests\\ApiExceptionTest'>"
            + "<testcase classname='Tests.ApiExceptionTest' name='keeps a cause'/>"
            + "<testsuite name='nested'>"
            + "<testcase classname='Tests.ApiExceptionTest' name='keeps a cause with data set \"a\"'/>"
            + "<testcase classname='Tests.ApiExceptionTest' name='keeps a cause with data set \"b\"'/>"
            + "</testsuite></testsuite></testsuite></testsuites>");

    ParitySummary summary = ParityReportParser.parse("php", reports, 1L);

    assertThat(summary.total()).isEqualTo(3);
    assertThat(summary.keys()).containsExactly("apierror::keepsacause");
  }

  @Test
  void shouldIgnoreFilesThatAreNotXmlReports() throws IOException {
    write("TEST-a.xml", "<testsuite name='x'><testcase classname='A' name='test_one'/></testsuite>");
    Files.write(reports.resolve("notes.txt"), new byte[] {1});
    write("other.xml", "<coverage/>");

    ParitySummary summary = ParityReportParser.parse("python", reports, 1L);

    assertThat(summary.total()).isEqualTo(1);
    assertThat(summary.errors()).isEmpty();
  }

  @Test
  void shouldRecordAReportThatIsNotWellFormedInsteadOfSkippingIt() throws IOException {
    write(
        "junit.xml",
        "<testsuites><testsuite name='t'><testcase classname='test.x' name='a\u000cb'/>"
            + "</testsuite></testsuites>");

    ParitySummary summary = ParityReportParser.parse("dart", reports, 1L);

    assertThat(summary.total()).isZero();
    assertThat(summary.errors()).hasSize(1);
    assertThat(summary.errors().get(0)).contains("cannot read junit.xml");
  }

  @Test
  void shouldRecordAMissingReportsDirectory() {
    ParitySummary summary = ParityReportParser.parse("go", reports.resolve("absent"), 1L);

    assertThat(summary.total()).isZero();
    assertThat(summary.errors()).hasSize(1);
    assertThat(summary.errors().get(0)).contains("no reports directory");
  }

  @Test
  void shouldReportZeroTestsForAnEmptyDirectoryWithoutAnError() {
    ParitySummary summary = ParityReportParser.parse("go", reports, 1L);

    assertThat(summary.total()).isZero();
    assertThat(summary.keys()).isEmpty();
  }
}
