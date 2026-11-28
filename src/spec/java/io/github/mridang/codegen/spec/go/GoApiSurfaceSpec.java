package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Go SDK (D180): a small {@code go/types} program reads the
 * compiler's export data of every package under {@code ./pkg} and lists its exported declarations,
 * one signature per line with every type qualified by its import path. If this test fails on the
 * snapshot, the public API changed; if it fails on a leak, a public signature names a type of a
 * module the SDK depends on.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoApiSurfaceSpec extends AbstractApiSurfaceSpec implements GoSpec {

    @Override
    protected List<String> getExtractorScripts() {
        return List.of("go-surface.go");
    }

    @Override
    protected String[] getSurfaceCommands() {
        /* Run from outside the module: the program imports the standard library only and asks
         * `go list` in /work for the export data. */
        return new String[] {"cd /tmp/api-surface && go run go-surface.go /work"};
    }

    @Override
    protected List<Pattern> getThirdPartyPatterns() {
        return List.of(
                Pattern.compile("\\bgo\\.opentelemetry\\.io/"),
                Pattern.compile("\\bgithub\\.com/go-json-experiment/json"),
                Pattern.compile("\\bgithub\\.com/google/uuid"),
                Pattern.compile("\\bgithub\\.com/shopspring/decimal"),
                Pattern.compile("\\bcloud\\.google\\.com/go/"),
                Pattern.compile("\\bgithub\\.com/andybalholm/brotli"),
                Pattern.compile("\\bgithub\\.com/klauspost/compress"));
    }
}
