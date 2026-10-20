package io.github.mridang.codegen.spec.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinLintingSpec extends AbstractIntegrationSpec implements KotlinSpec {

    @Override
    protected String[] getBuildCommands() {
        // detekt over the main and test sources, using the generated
        // detekt.yml. Its exit code is the result: no fallback that passes
        // the spec when the task fails.
        return new String[] {"gradle detekt --console=plain"};
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Kotlin code has linting violations:\n%s", result.output())
                .isTrue();
    }
}
