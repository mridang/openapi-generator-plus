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
        return List.of("go mod tidy");
    }

    @Override
    default Map<String, String> getCacheEnv() {
        /* GOPATH owns the module cache ($GOPATH/pkg/mod) — stable deps,
         * cached across runs under /root/.cache. GOCACHE owns the
         * incremental compile cache (build output) — re-derived each run
         * with the regenerated client, so we keep it ephemeral in /tmp
         * (shared across specs in the long-lived container, not persisted
         * between CI runs). */
        return Map.of(
                "GOPATH", "/root/.cache/go/path",
                "GOCACHE", "/tmp/build/go/build");
    }
}
