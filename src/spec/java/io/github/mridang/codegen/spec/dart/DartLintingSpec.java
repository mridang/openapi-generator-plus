package io.github.mridang.codegen.spec.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartLintingSpec extends AbstractIntegrationSpec implements DartSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"dart pub get", "dart analyze"};
    }

    @Test
    void generatedCodeShouldPassLinting() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Dart code has linting violations:\n%s", result.output())
                .isTrue();
    }
}
