package io.github.mridang.codegen.spec.parity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the JUnit XML reports of one language into a {@link ParitySummary}. Handles both the
 * {@code <testsuites>} root of a combined report and the {@code <testsuite>} root of a per-class
 * one, and every level of nested suites. A report that is not well-formed XML (a form feed in a
 * Dart test name once made the whole file unreadable) is recorded as an error rather than
 * skipped, because a language whose tests cannot be read must not pass as having none missing.
 */
public final class ParityReportParser {

  private ParityReportParser() {}

  /**
   * Parses every {@code *.xml} file directly under {@code reportsDir}.
   *
   * @param language the short language id
   * @param reportsDir the folder the language's test runner wrote its reports to; may be missing
   * @param writtenAt the time to record in the summary
   */
  public static ParitySummary parse(String language, Path reportsDir, long writtenAt) {
    SortedSet<String> keys = new TreeSet<>();
    List<String> errors = new ArrayList<>();
    int[] total = {0};
    if (!Files.isDirectory(reportsDir)) {
      errors.add("no reports directory at " + reportsDir);
      return new ParitySummary(language, writtenAt, 0, keys, errors);
    }
    List<Path> files;
    try (Stream<Path> stream = Files.list(reportsDir)) {
      files =
          stream
              .filter(p -> p.getFileName().toString().endsWith(".xml"))
              .sorted()
              .collect(Collectors.toList());
    } catch (IOException e) {
      errors.add("cannot list " + reportsDir + ": " + e.getMessage());
      return new ParitySummary(language, writtenAt, 0, keys, errors);
    }
    for (Path file : files) {
      try {
        collect(language, document(file).getDocumentElement(), keys, total);
      } catch (Exception e) {
        errors.add("cannot read " + file.getFileName() + ": " + e.getMessage());
      }
    }
    return new ParitySummary(language, writtenAt, total[0], keys, errors);
  }

  private static Document document(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(file.toFile());
  }

  private static void collect(String language, Element element, SortedSet<String> keys, int[] total) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element childElement = (Element) child;
      if ("testcase".equals(childElement.getTagName()) && "testsuite".equals(element.getTagName())) {
        total[0]++;
        keys.add(
            TestNameNormaliser.normalise(
                    language,
                    childElement.getAttribute("classname"),
                    element.getAttribute("name"),
                    childElement.getAttribute("name"))
                .canonical());
      } else {
        collect(language, childElement, keys, total);
      }
    }
  }
}
