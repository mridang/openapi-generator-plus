package io.github.mridang.codegen.spec.swift;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftLintingSpec extends AbstractIntegrationSpec implements SwiftSpec {

    @Override
    protected String[] getBuildCommands() {
        /* The two checks the generated Makefile's build and lint targets run,
         * over the sources and the tests alike: the compiler (with
         * -warnings-as-errors from Package.swift, and --build-tests so the
         * test target is type-checked too) and swift-format's linter.
         * SwiftLint is not run here: it ships no Linux binary and building it
         * from source inside the spec container costs more than it finds on
         * top of swift-format. */
        return new String[] {
            "swift build --build-tests", "swift-format lint --strict --recursive Sources Tests"
        };
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Swift code has build errors:\n%s", result.output())
                .isTrue();
    }
}
