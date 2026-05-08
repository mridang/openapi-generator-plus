package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("rust")
interface RustSpec extends LanguageSpec, DockerImageSpec {

    @Override
    default String getGeneratorName() {
        return "rust-plus";
    }

    @Override
    default DockerImageName getRuntimeImage() {
        return DockerImageName.parse("rust:1.88");
    }

    @Override
    default String getDockerImage() {
        return "rust:1.88";
    }

    @Override
    default Map<String, Object> getCodegenProperties() {
        return Map.of("packageName", "petstore");
    }
}
