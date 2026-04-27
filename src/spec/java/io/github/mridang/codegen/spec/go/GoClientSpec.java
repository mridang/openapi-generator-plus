package io.github.mridang.codegen.spec.go;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoClientSpec extends AbstractClientSpec implements GoSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"go mod tidy", "go test ./test/..."};
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("go.mod")).exists();
        assertThat(outputDir.resolve("pkg/client.go")).exists();
        assertThat(outputDir.resolve("pkg/pet_api.go")).exists();
        assertThat(outputDir.resolve("pkg/models")).isDirectory();
        assertThat(outputDir.resolve("pkg/errors")).isDirectory();
        assertThat(outputDir.resolve("pkg/auth")).isDirectory();
        assertThat(outputDir.resolve("pkg/options")).isDirectory();
    }
}
