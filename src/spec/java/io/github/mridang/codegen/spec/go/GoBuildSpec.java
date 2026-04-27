package io.github.mridang.codegen.spec.go;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoBuildSpec extends AbstractIntegrationSpec implements GoSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"go mod tidy", "go build ./..."};
    }

    @Test
    void generatedCodeShouldCompile() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Go code has compilation errors:\n%s", result.output())
                .isTrue();
    }
}
