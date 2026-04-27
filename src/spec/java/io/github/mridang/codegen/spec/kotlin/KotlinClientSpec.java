package io.github.mridang.codegen.spec.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinClientSpec extends AbstractClientSpec implements KotlinSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"gradle test"};
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("build.gradle.kts")).exists();
        assertThat(outputDir.resolve("src/main/kotlin/com/example/petstore/api/PetApi.kt"))
                .exists();
        assertThat(outputDir.resolve("src/main/kotlin/com/example/petstore/Client.kt")).exists();
    }
}
