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
            /* Dialyzer's type-information (PLT) files land in _build/dev, which
             * is wiped for every spec, so rebuilding them is the slowest step.
             * Restore them from the persisted cache first and save them back
             * afterwards; dialyxir revalidates a restored PLT, so a stale one
             * is rebuilt rather than trusted. */
            "mkdir -p _build/dev && cp -a /root/.cache/elixir/plt/*.plt* _build/dev/ 2>/dev/null; true",
            "timeout -k 60 1800 mix dialyzer",
            "mkdir -p /root/.cache/elixir/plt && cp -a _build/dev/*.plt* /root/.cache/elixir/plt/ 2>/dev/null; true"
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
