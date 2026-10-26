package io.github.mridang.codegen.spec.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinBuildSpec extends AbstractIntegrationSpec implements KotlinSpec {

    @Override
    protected String[] getBuildCommands() {
        // Compiles the main and the test sources. `gradle build` would also
        // run the integration tests, which KotlinClientSpec owns; the exit
        // code is the result, with no fallback that passes on failure.
        return new String[] {"gradle assemble compileTestKotlinJvm --console=plain"};
    }

    @Test
    void generatedCodeShouldCompile() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Kotlin code has compilation errors:\n%s", result.output())
                .isTrue();
    }
}
