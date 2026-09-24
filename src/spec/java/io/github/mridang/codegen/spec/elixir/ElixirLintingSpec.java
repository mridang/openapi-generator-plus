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
            /* Each step is bounded: a tool that hangs -- credo and dialyzer
             * have both sat at idle CPU with no output for over 20 minutes --
             * stalls every other language behind it and reports nothing. A
             * timeout turns that into a failure naming the step. */
            "timeout -k 30 600 mix compile --warnings-as-errors --force",
            "timeout -k 30 900 mix credo --strict",
            "timeout -k 60 1800 mix dialyzer"
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
