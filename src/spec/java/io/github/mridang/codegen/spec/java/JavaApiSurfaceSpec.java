package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Java SDK (D180): the SDK is compiled and {@code javap
 * -protected} lists every public and protected member of its classes, which the extractor turns
 * into one signature per line. If this test fails on the snapshot, the public API changed; if it
 * fails on a leak, a public signature names a Jackson, OpenTelemetry or other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaApiSurfaceSpec extends AbstractApiSurfaceSpec implements JavaSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("java-surface.sh");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {"mvn compile -q -B", "sh /tmp/api-surface/java-surface.sh target/classes"};
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    return List.of(
        Pattern.compile("\\bcom\\.fasterxml\\.jackson\\."),
        Pattern.compile("\\borg\\.openapitools\\."),
        Pattern.compile("\\bio\\.opentelemetry\\."),
        Pattern.compile("\\borg\\.brotli\\."),
        Pattern.compile("\\bcom\\.github\\.luben\\."),
        Pattern.compile("\\bedu\\.umd\\.cs\\.findbugs\\."),
        Pattern.compile("\\bjakarta\\.annotation\\."),
        Pattern.compile("\\bjavax\\.annotation\\.(Nullable|Nonnull|CheckForNull)\\b"));
  }
}
