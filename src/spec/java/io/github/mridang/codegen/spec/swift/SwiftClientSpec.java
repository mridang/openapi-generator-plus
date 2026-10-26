package io.github.mridang.codegen.spec.swift;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftClientSpec extends AbstractClientSpec implements SwiftSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {
            /* The report printed after this run is read from the host's golden
             * directory; clear the previous run's there, so a run that fails
             * before writing one does not print stale results. */
            "find /app/.out/reports -mindepth 1 -delete 2>/dev/null; mkdir -p .out/reports",
            "swift test --parallel --enable-code-coverage --enable-swift-testing --disable-xctest --xunit-output .out/reports/junit.xml"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("Package.swift")).exists();
        assertThat(outputDir.resolve("Sources")).isDirectory();
        assertThat(outputDir.resolve("Tests")).isDirectory();
    }
}
