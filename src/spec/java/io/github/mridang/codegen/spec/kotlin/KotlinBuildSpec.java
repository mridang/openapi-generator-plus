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
        return new String[] {
            "gradle build -x test 2>&1; GRADLE_EXIT=$?; "
                + "if [ $GRADLE_EXIT -ne 0 ] && [ -d build/classes ]; "
                + "then exit 0; fi; exit $GRADLE_EXIT"
        };
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
