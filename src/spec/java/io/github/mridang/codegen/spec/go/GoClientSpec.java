package io.github.mridang.codegen.spec.go;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoClientSpec extends AbstractClientSpec implements GoSpec {

    @Override
    protected String[] getBuildCommands() {
        /* gotestsum runs `go test` unchanged and also writes a JUnit XML into
         * `.out/reports/`, so the per-test counts are reported like the other
         * languages'. Its exit code is the `go test` exit code. */
        return new String[] {
            /* The report printed after this run is read from the host's golden
             * directory; clear the previous run's there, so a run that fails
             * before writing one does not print stale results. */
            "rm -rf /app/.out/reports && mkdir -p .out/reports",
            "go run gotest.tools/gotestsum@v1.13.0 --format=standard-quiet"
                + " --junitfile .out/reports/junit.xml -- -parallel=8 ./..."
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("go.mod")).exists();
        assertThat(outputDir.resolve("pkg/client.go")).exists();
        assertThat(outputDir.resolve("pkg/pet_api.go")).exists();
        assertThat(outputDir.resolve("pkg/models")).isDirectory();
        assertThat(outputDir.resolve("pkg/errors")).isDirectory();
        assertThat(outputDir.resolve("pkg/auth")).isDirectory();
        assertThat(outputDir.resolve("pkg/options")).isDirectory();
    }
}
