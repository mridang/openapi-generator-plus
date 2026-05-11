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
            "mkdir -p .out/reports",
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
