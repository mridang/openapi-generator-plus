package io.github.mridang.codegen.spec.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Dart code passes `dart analyze` with the same
 * severity the generated Makefile's `analyze` target uses, over both lib/ and
 * test/, so an info-level finding is a failure here as it is for a real
 * client. If this test fails, the Dart templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartLintingSpec extends AbstractIntegrationSpec implements DartSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"dart analyze --fatal-warnings --fatal-infos"};
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
