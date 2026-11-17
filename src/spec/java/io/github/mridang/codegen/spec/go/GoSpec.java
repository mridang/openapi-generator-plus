package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("go")
interface GoSpec extends LanguageSpec, DockerImageSpec {

    @Override
    default String getGeneratorName() {
        return "go-plus";
    }

    @Override
    default DockerImageName getRuntimeImage() {
        return DockerImageName.parse("golang:1.26-alpine");
    }

    @Override
    default String getDockerImage() {
        return "golang:1.26-alpine";
    }

    @Override
    default Map<String, Object> getCodegenProperties() {
        return Map.of("packageName", "petstore");
    }

    @Override
    default List<String> getSetupCommands() {
        /* The snapshot every spec restores is taken from the host's golden
         * directory, which still holds the previous run's JUnit report. Drop it,
         * or each later spec copies that stale report back over the fresh one.
         * `go mod tidy -diff` checks the committed go.mod/go.sum without
         * rewriting them (it exits non-zero and prints the diff when they are
         * untidy), so every spec runs on the manifest as committed. */
        return List.of(
                "find .out/reports -mindepth 1 -delete 2>/dev/null; true",
                "go mod tidy -diff",
                /* The Go image is Alpine and ships without make, which the SDK's own
                 * docs target needs. Installed once with the shared container, with
                 * retries because the package mirror is flaky on CI runners. */
                "for i in 1 2 3 4 5; do apk add --no-cache make && break;"
                        + " echo \"apk attempt $i failed; retrying\" >&2; sleep 3; done;"
                        + " command -v make > /dev/null"
                        + " || { echo \"make missing after retries\" >&2; exit 1; }");
    }

    @Override
    default Map<String, String> getCacheEnv() {
        /* GOPATH owns the module cache ($GOPATH/pkg/mod); GOCACHE owns the
         * incremental compile cache. Together they cover Go's full
         * cross-invocation state. */
        return Map.of(
                "GOPATH", "/root/.cache/go/path",
                "GOCACHE", "/root/.cache/go/build",
                /* golangci-lint keeps its own analysis cache keyed by file
                 * content and linter version. */
                "GOLANGCI_LINT_CACHE", "/root/.cache/go/golangci-lint");
    }
}
