package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Ruby SDK (D180): YARD writes its registry, and a script
 * requires the gem, walks its constants with reflection and reads the documented types and the
 * {@code @api private} markers from that registry, printing one signature per line. If this test
 * fails on the snapshot, the public API changed; if it fails on a leak, a public signature names
 * a Faraday, dry-rb, ISO8601, Tod, OpenTelemetry or other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyApiSurfaceSpec extends AbstractApiSurfaceSpec implements RubySpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("ruby-surface.rb");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {
      "bundle exec yardoc --no-output --no-stats --quiet",
      "bundle exec ruby /tmp/api-surface/ruby-surface.rb petstore_client Petstore::Client"
    };
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    return List.of(
        Pattern.compile(
            "(?<![\\w:])(Faraday|Dry|ISO8601|Tod|OpenTelemetry|Brotli|Zstd|Net::HTTP::Persistent"
                + "|Iso8601)(::|\\b)"));
  }
}
