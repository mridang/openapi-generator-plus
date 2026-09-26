package io.github.mridang.codegen.spec.rust;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustLintingSpec extends AbstractIntegrationSpec implements RustSpec {

    @Override
    protected String[] getBuildCommands() {
        /* The generated Makefile's lint target: clippy over every target (the
         * library, its in-crate tests and the tests/ crates) with every feature
         * enabled, so the OpenTelemetry integration and its tests are linted too.
         * Then its docs target, whose rustdoc lints run with -D warnings. */
        return new String[] {
            "CARGO_BUILD_JOBS=2 cargo clippy --all-targets --all-features -- -D warnings",
            "RUSTDOCFLAGS=\"-D warnings\" CARGO_BUILD_JOBS=2 cargo doc --no-deps --all-features"
        };
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Rust code has linting violations:\n%s", result.output())
                .isTrue();
    }
}
