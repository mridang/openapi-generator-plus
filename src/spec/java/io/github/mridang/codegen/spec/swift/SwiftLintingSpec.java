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
        /* The two checks the generated Makefile's build and lint targets run:
         * the compiler with -warnings-as-errors (from Package.swift) over the
         * sources, and swift-format's linter in strict mode over the sources
         * AND the tests. The test target is type-checked by SwiftClientSpec's
         * `swift test`, so building it a second time here only doubles the
         * container's peak memory. SwiftLint is not run: it ships no Linux
         * binary, and building it from source in the spec container costs
         * more than it finds on top of swift-format. */
        return new String[] {
            "swift build", "swift-format lint --strict --recursive Sources Tests"
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
