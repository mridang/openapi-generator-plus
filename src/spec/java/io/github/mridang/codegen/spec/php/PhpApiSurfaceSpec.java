package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated PHP SDK (D180): a script loads every class the composer
 * autoloader maps to {@code lib/} and reads it with Reflection and the docblock reader the SDK
 * already depends on, printing one signature per line with every class fully qualified. If this
 * test fails on the snapshot, the public API changed; if it fails on a leak, a public signature
 * names a PSR, Symfony, php-ds, brick/math, OpenTelemetry or other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpApiSurfaceSpec extends AbstractApiSurfaceSpec implements PhpSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("php-surface.php");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {"php /tmp/api-surface/php-surface.php"};
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    return List.of(
        Pattern.compile(
            "(^|[^\\w\\\\])(Brick|Symfony|Psr|Ds|OpenTelemetry|phpDocumentor|Nyholm|GuzzleHttp"
                + "|Http)\\\\"));
  }
}
