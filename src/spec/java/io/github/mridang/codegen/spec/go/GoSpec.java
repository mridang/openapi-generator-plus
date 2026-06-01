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
        return DockerImageName.parse("golang:1.25");
    }

    @Override
    default String getDockerImage() {
        return "golang:1.25";
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
        /* GOPATH owns the module cache ($GOPATH/pkg/mod); GOCACHE owns the
         * incremental compile cache. Together they cover Go's full
         * cross-invocation state. */
        return Map.of(
                "GOPATH", "/root/.cache/go/path",
                "GOCACHE", "/root/.cache/go/build");
    }
}
