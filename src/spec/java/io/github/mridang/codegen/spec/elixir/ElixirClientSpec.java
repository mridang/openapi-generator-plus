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
            /* Not --warnings-as-errors: the suite has to call the operations
             * the spec marks deprecated, and Elixir emits a deprecation
             * warning at every such call site with no way to suppress one.
             * The sources are compiled with warnings as errors by
             * ElixirLintingSpec, and the test files are linted there too, by
             * `mix credo --strict` over the generated .credo.exs, whose
             * `included` list covers test/. */
            "mix test"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("mix.exs")).exists();
        assertThat(outputDir.resolve("lib")).isDirectory();
        assertThat(outputDir.resolve("test")).isDirectory();
    }
}
