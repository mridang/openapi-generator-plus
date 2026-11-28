package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Python SDK (D180): a script imports the installed package
 * and reads every name its packages export with {@code inspect} and {@code typing}, printing one
 * signature per line with every class fully qualified. If this test fails on the snapshot, the
 * public API changed; if it fails on a leak, a public signature names a pydantic, urllib3,
 * OpenTelemetry or other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-python")
public class PythonApiSurfaceSpec extends AbstractApiSurfaceSpec implements PythonSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("python-surface.py");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {"python -W ignore /tmp/api-surface/python-surface.py petstore_client"};
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    return List.of(
        Pattern.compile(
            "(?<![\\w.])(pydantic|pydantic_core|urllib3|dateutil|opentelemetry|brotli|zstandard"
                + "|typing_extensions)\\."));
  }
}
