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
        /* The three checks the generated Makefile's vet, staticcheck and lint
         * targets run, over the sources and the tests alike: go vet,
         * staticcheck (a tool dependency in go.mod) and golangci-lint with the
         * generated .golangci.yml. */
        return new String[] {
            "go vet ./...",
            "go tool staticcheck ./...",
            "go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@v2.13.2",
            "\"$GOPATH/bin/golangci-lint\" run ./..."
        };
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
