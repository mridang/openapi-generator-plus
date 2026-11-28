package io.github.mridang.codegen.spec.kotlin;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Kotlin SDK (D180): the Kotlin Gradle plugin's ABI
 * validation dumps the public JVM ABI of the SDK (leaving out {@code internal} declarations), and
 * the extractor turns it into one signature per line. If this test fails on the snapshot, the
 * public API changed; if it fails on a leak, a public signature names a Ktor, kotlinx or other
 * third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinApiSurfaceSpec extends AbstractApiSurfaceSpec implements KotlinSpec {

    @Override
    protected List<String> getExtractorScripts() {
        return List.of("kotlin-surface.sh");
    }

    @Override
    protected String[] getSurfaceCommands() {
        return new String[] {"sh /tmp/api-surface/kotlin-surface.sh"};
    }

    @Override
    protected List<Pattern> getThirdPartyPatterns() {
        return List.of(
                Pattern.compile("\\bio[/.]ktor[/.]"),
                Pattern.compile("\\bkotlinx[/.]serialization[/.]"),
                Pattern.compile("\\bkotlinx[/.]coroutines[/.]"),
                Pattern.compile("\\bkotlinx[/.]datetime[/.]"),
                Pattern.compile("\\bio[/.]opentelemetry[/.]"),
                Pattern.compile("\\bcom[/.]fasterxml[/.]jackson[/.]"),
                Pattern.compile("\\borg[/.]brotli[/.]"),
                Pattern.compile("\\bcom[/.]github[/.]luben[/.]"));
    }
}
