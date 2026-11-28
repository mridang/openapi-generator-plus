package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Swift SDK (D180): SwiftPM writes the symbol graph of the
 * library target, and a Swift script reads it and prints one signature per public symbol and
 * conformance. If this test fails on the snapshot, the public API changed; if it fails on a leak,
 * a public signature names a type of a module outside the standard library and Foundation.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftApiSurfaceSpec extends AbstractApiSurfaceSpec implements SwiftSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("swift-surface.swift");
  }

  @Override
  protected String[] getSurfaceCommands() {
    /* The symbol graph directory sits under the build directory, which SWIFTPM_BUILD_DIR
     * relocates and whose name carries the target triple, so it is read from the command's own
     * report. */
    return new String[] {
      "swift package dump-symbol-graph --skip-synthesized-members"
          + " > /tmp/api-surface/dump.txt 2>&1 || { cat /tmp/api-surface/dump.txt; exit 1; }",
      "swift /tmp/api-surface/swift-surface.swift"
          + " \"$(sed -n 's/^Files written to //p' /tmp/api-surface/dump.txt)\" PetstoreClient"
    };
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    /* The extractor ends a signature with "[uses <Module>.<Type>, ...]" for every type of a
     * module outside the standard library and Foundation, and names such a module in a
     * conformance; the library target itself depends on no package. */
    return List.of(
        Pattern.compile("\\[uses "),
        Pattern.compile("\\b(OpenTelemetryApi|OpenTelemetrySdk|Testcontainers|SwiftDocCPlugin)\\."));
  }
}
