package io.github.mridang.codegen.spec;

import java.util.Map;
import org.testcontainers.utility.DockerImageName;

public interface LanguageSpec {

  String getGeneratorName();

  DockerImageName getRuntimeImage();

  default Map<String, Object> getCodegenProperties() {
    return Map.of();
  }
}
