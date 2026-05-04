package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.testcontainers.utility.DockerImageName;

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
}
