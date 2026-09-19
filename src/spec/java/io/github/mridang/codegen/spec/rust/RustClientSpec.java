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
            /* Compile ALL test binaries (lib in its `--cfg test` variant plus
             * every integration-test crate) in a single bounded build BEFORE the
             * run. Unit tests live in-crate (`#[cfg(test)] mod tests`), so the
             * lib is built in two variants; if the lib's test-cfg rlib is still
             * being emitted when nextest starts compiling a dependent
             * integration-test crate in parallel, that crate fails with a
             * transient `E0463: can't find crate petstore` (and, by extension,
             * `serde`/`testcontainers`/… whichever dependent loses the race).
             * `cargo nextest run --no-run` performs the identical test-profile
             * build nextest would otherwise do inline, so the subsequent run
             * step finds every artifact cached and never rebuilds concurrently.
             * CARGO_BUILD_JOBS=2 bounds the build graph's parallelism (matching
             * RustBuildSpec) so the rlib lands before its dependents compile. */
            "CARGO_BUILD_JOBS=2 cargo nextest run --profile=ci --no-run",
            "CARGO_BUILD_JOBS=2 cargo nextest run --profile=ci",
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
