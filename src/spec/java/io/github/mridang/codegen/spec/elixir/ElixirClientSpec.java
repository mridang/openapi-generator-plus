package io.github.mridang.codegen.spec.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirClientSpec extends AbstractClientSpec implements ElixirSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {
            /* The report printed after this run is read from the host's golden
             * directory; clear the previous run's there, so a run that fails
             * before writing one does not print stale results. */
            "rm -rf /app/.out/reports && mkdir -p .out/reports",
            /* Test files compile only when the suite runs, so their warnings
             * fail the run here. */
            "mix test --warnings-as-errors"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("mix.exs")).exists();
        assertThat(outputDir.resolve("lib")).isDirectory();
        assertThat(outputDir.resolve("test")).isDirectory();
    }
}
