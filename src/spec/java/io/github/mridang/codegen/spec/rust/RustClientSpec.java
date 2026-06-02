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
         * a JUnit XML at `target/nextest/<profile>/junit.xml`. The `ci`
         * profile is defined in `.config/nextest.toml` (written by
         * RustSpec.getSetupCommands()). Copy the XML into the spec's
         * `.out/reports/` so AbstractIntegrationSpec's post-step picks
         * it up alongside the other languages' JUnit output.
         *
         * The path is relative to CARGO_TARGET_DIR (set in RustSpec to
         * /root/.cache/rust/target), so the XML actually lands at
         * /root/.cache/rust/target/nextest/ci/junit.xml — the shell
         * resolves $CARGO_TARGET_DIR before nextest sees it. */
        return new String[] {
            "mkdir -p .out/reports",
            "cargo nextest run --profile=ci",
            "cp $CARGO_TARGET_DIR/nextest/ci/junit.xml .out/reports/junit.xml"
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
