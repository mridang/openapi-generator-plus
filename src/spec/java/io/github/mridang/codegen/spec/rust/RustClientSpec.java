package io.github.mridang.codegen.spec.rust;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustClientSpec extends AbstractClientSpec implements RustSpec {

    @Override
    protected String[] getBuildCommands() {
        /* `cargo nextest` runs the same tests as `cargo test` but writes
         * a JUnit XML at `<workspace>/target/nextest/<profile>/junit.xml`.
         * The `ci` profile is defined in `.config/nextest.toml` (written
         * by RustSpec.getSetupCommands()).
         *
         * Note the path: nextest puts test BINARIES under
         * `$CARGO_TARGET_DIR/` (respecting the env var) but the JUnit
         * XML lands relative to the WORKSPACE ROOT, regardless of
         * CARGO_TARGET_DIR. Verified empirically in docker against the
         * same image (rust:1.88) and the same CARGO_TARGET_DIR redirect
         * that CI uses (/root/.cache/rust/target):
         *
         *   $CARGO_TARGET_DIR/nextest/ci/junit.xml  → MISSING
         *   /work/target/nextest/ci/junit.xml       → FOUND
         *
         * So we cp from the workspace-local path. Copy into the spec's
         * `.out/reports/` so AbstractIntegrationSpec's post-step picks
         * it up alongside the other languages' JUnit output. */
        return new String[] {
            "mkdir -p .out/reports",
            /* Build the library (normal rlib) BEFORE nextest. Since unit tests
             * now live in-crate (`#[cfg(test)] mod tests`), the lib is compiled
             * in two variants; nextest's parallel test-binary build could start
             * an integration-test crate before the lib's normal rlib was
             * emitted, yielding a transient `E0463: can't find crate petstore`.
             * Building the lib first makes the rlib present and removes the race.
             * CARGO_BUILD_JOBS=2 matches RustBuildSpec (bounds peak memory). */
            "CARGO_BUILD_JOBS=2 cargo build",
            "cargo nextest run --profile=ci",
            "cp target/nextest/ci/junit.xml .out/reports/junit.xml"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("Cargo.toml")).exists();
        assertThat(outputDir.resolve("src/lib.rs")).exists();
        assertThat(outputDir.resolve("src/api/pet_api.rs")).exists();
        assertThat(outputDir.resolve("src/models/pet.rs")).exists();
        assertThat(outputDir.resolve("src/client.rs")).exists();
    }
}
