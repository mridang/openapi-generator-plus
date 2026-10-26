package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Walks a directory of JUnit XML files, parses each one, and prints a unified
 * test summary report. Handles both {@code <testsuites>} root (single combined
 * file from pytest/phpunit/jest/xunit/minitest) and {@code <testsuite>} root
 * (per-class file from Maven Surefire).
 */
public final class JUnitReportPrinter {

  private JUnitReportPrinter() {}

  /**
   * Parses every {@code *.xml} file in {@code reportsDir} and prints a summary
   * report via {@code logger.info(...)}. Missing/empty directories and malformed
   * files are logged as warnings but never throw.
   */
  public static void printReport(Path reportsDir, String language, Logger logger) {
    if (!Files.isDirectory(reportsDir)) {
      logger.warn("No JUnit reports directory at {}", reportsDir);
      return;
    }

    List<SuiteSummary> suites = new ArrayList<>();
    List<TestFailure> failures = new ArrayList<>();

    try (Stream<Path> files = Files.list(reportsDir)) {
      List<Path> xmlFiles =
          files
              .filter(p -> p.getFileName().toString().endsWith(".xml"))
              .sorted()
              .collect(Collectors.toList());

      if (xmlFiles.isEmpty()) {
        logger.warn("No JUnit XML files found in {}", reportsDir);
        return;
      }

      for (Path file : xmlFiles) {
        try {
          parseFile(file, suites, failures);
        } catch (Exception e) {
          logger.warn("Failed to parse {}: {}", file, e.getMessage());
        }
      }
    } catch (IOException e) {
      logger.warn("Failed to list {}: {}", reportsDir, e.getMessage());
      return;
    }

    if (suites.isEmpty()) {
      logger.warn("No test suites found in {}", reportsDir);
      return;
    }

    printSummary(suites, failures, language, logger);
  }

  private static void parseFile(Path file, List<SuiteSummary> suites, List<TestFailure> failures)
      throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(file.toFile());
    Element root = doc.getDocumentElement();

    if ("testsuites".equals(root.getTagName())) {
      collectSuites(root, suites, failures);
    } else if ("testsuite".equals(root.getTagName())) {
      collectSuite(root, suites, failures);
    }
  }

  private static void collectSuites(
      Element parent, List<SuiteSummary> suites, List<TestFailure> failures) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && "testsuite".equals(child.getNodeName())) {
        Element suite = (Element) child;
        // Recurse for nested testsuites (e.g. PHPUnit nests by directory).
        boolean hasChildSuites = false;
        NodeList suiteChildren = suite.getChildNodes();
        for (int j = 0; j < suiteChildren.getLength(); j++) {
          Node sc = suiteChildren.item(j);
          if (sc.getNodeType() == Node.ELEMENT_NODE && "testsuite".equals(sc.getNodeName())) {
            hasChildSuites = true;
            break;
          }
        }
        if (hasChildSuites) {
          /* A suite can hold both its own cases and nested suites — Pest
           * nests a describe() block inside the file's suite. Recursing
           * alone dropped every test declared outside the describe, so the
           * report undercounted what the runner printed. */
          collectDirectCases(suite, suites, failures);
          collectSuites(suite, suites, failures);
        } else {
          collectSuite(suite, suites, failures);
        }
      }
    }
  }

  /**
   * Summarises only the {@code <testcase>} elements that are direct children of
   * {@code suite}, for a suite that also holds nested suites. The suite's own
   * {@code tests}/{@code time} attributes cover its descendants too, so the
   * counts are recomputed from the direct cases to avoid double counting.
   */
  private static void collectDirectCases(
      Element suite, List<SuiteSummary> suites, List<TestFailure> failures) {
    String name = attr(suite, "name", "(unnamed)");
    List<Element> direct = new ArrayList<>();
    NodeList children = suite.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && "testcase".equals(child.getNodeName())) {
        direct.add((Element) child);
      }
    }
    if (direct.isEmpty()) {
      return;
    }

    int failed = 0;
    int errored = 0;
    int skipped = 0;
    double time = 0;
    for (Element tc : direct) {
      time += parseDouble(attr(tc, "time", "0"));
      if (tc.getElementsByTagName("failure").getLength() > 0) {
        failed++;
      } else if (tc.getElementsByTagName("error").getLength() > 0) {
        errored++;
      } else if (tc.getElementsByTagName("skipped").getLength() > 0) {
        skipped++;
      }
    }
    suites.add(new SuiteSummary(name, direct.size(), failed, errored, skipped, time));
    collectFailures(suite, name, failures);
  }

  private static void collectSuite(
      Element suite, List<SuiteSummary> suites, List<TestFailure> failures) {
    String name = attr(suite, "name", "(unnamed)");
    int tests = parseInt(attr(suite, "tests", "0"));
    int failed = parseInt(attr(suite, "failures", "0"));
    int errored = parseInt(attr(suite, "errors", "0"));
    int skipped = parseInt(attr(suite, "skipped", "0"));
    double time = parseDouble(attr(suite, "time", "0"));

    NodeList testcases = suite.getElementsByTagName("testcase");
    if (tests == 0 && testcases.getLength() == 0) {
      return;
    }

    /* Count the <testcase> elements rather than trusting the suite's own
     * attributes. Writers disagree about what `tests` means: swift-testing
     * excludes the skipped cases from it, so subtracting `skipped` again
     * dropped a test from the report for every test the runner skipped, and
     * the printed totals never matched what the runner announced. The
     * elements are the tests that ran, so they are what gets counted; the
     * attributes stay as the fallback for a writer that emits no elements. */
    int countedTests = 0;
    int countedSkipped = 0;
    int countedFailed = 0;
    for (int i = 0; i < testcases.getLength(); i++) {
      Node tcNode = testcases.item(i);
      if (tcNode.getParentNode() != suite) {
        continue;
      }
      Element tc = (Element) tcNode;
      countedTests++;
      if (tc.getElementsByTagName("skipped").getLength() > 0) {
        countedSkipped++;
      } else if (tc.getElementsByTagName("failure").getLength() > 0
          || tc.getElementsByTagName("error").getLength() > 0) {
        countedFailed++;
      }
    }
    if (countedTests > 0) {
      tests = countedTests;
      skipped = countedSkipped;
      failed = countedFailed;
      errored = 0;
    }

    suites.add(new SuiteSummary(name, tests, failed, errored, skipped, time));
    collectFailures(suite, name, failures);
  }

  /** Records every failing/erroring {@code <testcase>} directly under {@code suite}. */
  private static void collectFailures(Element suite, String name, List<TestFailure> failures) {
    NodeList testcases = suite.getElementsByTagName("testcase");
    for (int i = 0; i < testcases.getLength(); i++) {
      Node tcNode = testcases.item(i);
      if (tcNode.getParentNode() != suite) {
        continue;
      }
      Element tc = (Element) tcNode;
      NodeList failureNodes = tc.getElementsByTagName("failure");
      NodeList errorNodes = tc.getElementsByTagName("error");
      if (failureNodes.getLength() > 0 || errorNodes.getLength() > 0) {
        String classname = attr(tc, "classname", name);
        String testName = attr(tc, "name", "(unknown)");
        Element failureElement =
            failureNodes.getLength() > 0
                ? (Element) failureNodes.item(0)
                : (Element) errorNodes.item(0);
        String message = attr(failureElement, "message", "");
        if (message.isEmpty()) {
          message = failureElement.getTextContent().trim();
        }
        if (message.length() > 200) {
          message = message.substring(0, 200) + "...";
        }
        failures.add(new TestFailure(classname, testName, message));
      }
    }
  }

  private static void printSummary(
      List<SuiteSummary> suites, List<TestFailure> failures, String language, Logger logger) {
    int totalTests = 0;
    int totalFailed = 0;
    int totalErrored = 0;
    int totalSkipped = 0;
    double totalTime = 0;

    int nameWidth = 4;
    for (SuiteSummary s : suites) {
      totalTests += s.tests;
      totalFailed += s.failed;
      totalErrored += s.errored;
      totalSkipped += s.skipped;
      totalTime += s.time;
      if (s.name.length() > nameWidth) {
        nameWidth = s.name.length();
      }
    }

    String header = repeat('=', Math.max(40, nameWidth + 50));
    StringBuilder sb = new StringBuilder();
    sb.append('\n').append(header).append('\n');
    sb.append("Test Report (").append(language).append(")\n");
    sb.append(header).append('\n');

    String rowFormat = "%-" + nameWidth + "s  %5d tests  %5d ok  %5d fail  %5d skip  %7.2fs%n";
    for (SuiteSummary s : suites) {
      int ok = s.tests - s.failed - s.errored - s.skipped;
      sb.append(
          String.format(
              Locale.ROOT,
              rowFormat,
              s.name,
              s.tests,
              ok,
              s.failed + s.errored,
              s.skipped,
              s.time));
    }
    sb.append(repeat('-', header.length())).append('\n');
    int totalOk = totalTests - totalFailed - totalErrored - totalSkipped;
    sb.append(
        String.format(
            Locale.ROOT,
            rowFormat,
            "Total",
            totalTests,
            totalOk,
            totalFailed + totalErrored,
            totalSkipped,
            totalTime));
    sb.append(header).append('\n');

    if (!failures.isEmpty()) {
      sb.append("Failures:\n");
      for (TestFailure f : failures) {
        sb.append("  - ").append(f.classname).append('.').append(f.name);
        if (!f.message.isEmpty()) {
          sb.append(": ").append(f.message);
        }
        sb.append('\n');
      }
      sb.append(header).append('\n');
    }

    logger.info("{}", sb);
  }

  private static String repeat(char c, int count) {
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(c);
    }
    return sb.toString();
  }

  private static String attr(Element el, String name, String defaultValue) {
    String value = el.getAttribute(name);
    return value.isEmpty() ? defaultValue : value;
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double parseDouble(String s) {
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private static final class SuiteSummary {
    final String name;
    final int tests;
    final int failed;
    final int errored;
    final int skipped;
    final double time;

    SuiteSummary(String name, int tests, int failed, int errored, int skipped, double time) {
      this.name = name;
      this.tests = tests;
      this.failed = failed;
      this.errored = errored;
      this.skipped = skipped;
      this.time = time;
    }
  }

  private static final class TestFailure {
    final String classname;
    final String name;
    final String message;

    TestFailure(String classname, String name, String message) {
      this.classname = classname;
      this.name = name;
      this.message = message;
    }
  }
}
