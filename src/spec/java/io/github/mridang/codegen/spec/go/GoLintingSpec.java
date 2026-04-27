package io.github.mridang.codegen.spec.go;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoLintingSpec extends AbstractIntegrationSpec implements GoSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"go mod tidy", "go vet ./..."};
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Go code has linting violations:\n%s", result.output())
                .isTrue();
    }
}
