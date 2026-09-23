package io.github.mridang.codegen.spec.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirLintingSpec extends AbstractIntegrationSpec implements ElixirSpec {

    @Override
    protected String[] getBuildCommands() {
        /* The generated Makefile's build, lint and dialyzer targets: compile
         * with warnings as errors, Credo in strict mode over lib/ and test/
         * (the generated .credo.exs), and Dialyzer with the flags mix.exs
         * sets. */
        return new String[] {
            "mix compile --warnings-as-errors --force", "mix credo --strict", "mix dialyzer"
        };
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Elixir code has compilation warnings:\n%s", result.output())
                .isTrue();
    }
}
