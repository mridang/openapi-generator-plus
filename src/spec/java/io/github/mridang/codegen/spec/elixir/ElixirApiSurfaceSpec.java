package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Elixir SDK (D180): a script run with {@code mix run} reads
 * the documentation chunks and typespecs of the compiled application and prints one signature per
 * documented module, struct, function, type and callback. If this test fails on the snapshot, the
 * public API changed; if it fails on a leak, a public signature names a Req, Jason, Decimal or
 * other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirApiSurfaceSpec extends AbstractApiSurfaceSpec implements ElixirSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("elixir-surface.exs");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {"mix run --no-start /tmp/api-surface/elixir-surface.exs"};
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    return List.of(
        Pattern.compile(
            "(?<![\\w.])(Req|Jason|Decimal|MIME|Finch|Mint|Brotli|Telemetry|OpenTelemetry"
                + "|Testcontainers)\\."),
        Pattern.compile(":(telemetry|opentelemetry\\w*|otel_\\w+|brotli|ezstd|hackney)\\b"));
  }
}
