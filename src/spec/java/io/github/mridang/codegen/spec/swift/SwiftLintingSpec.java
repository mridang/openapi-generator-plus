package io.github.mridang.codegen.spec.swift;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftLintingSpec extends AbstractIntegrationSpec implements SwiftSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"swift build --build-system swiftbuild"};
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Swift code has build errors:\n%s", result.output())
                .isTrue();
    }
}
