package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Dart SDK (D180): a script run against the analyzer the
 * SDK's dev dependencies already resolve lists every declaration the package barrel exports, one
 * signature per line, and marks each type of another package a signature names. If this test
 * fails on the snapshot, the public API changed; if it fails on a leak, a public signature names
 * a type of {@code package:http}, {@code package:decimal} or another dependency.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartApiSurfaceSpec extends AbstractApiSurfaceSpec implements DartSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("dart-surface.dart");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {
      "dart run --packages=.dart_tool/package_config.json /tmp/api-surface/dart-surface.dart"
    };
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    /* The extractor ends a signature with "[uses <package>:<Type>, ...]" for every type of
     * another package it names, and lists a re-exported declaration as "export <name> from
     * <uri>". */
    String deps = "(http|http_parser|decimal|collection|mime|opentelemetry)";
    return List.of(
        Pattern.compile("\\[uses [^\\]]*\\b" + deps + ":"),
        Pattern.compile("^export \\S+ from package:" + deps + "/"));
  }
}
