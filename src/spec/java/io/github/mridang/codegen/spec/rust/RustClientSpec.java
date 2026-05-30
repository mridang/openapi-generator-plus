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
        return new String[] {"cargo test"};
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
