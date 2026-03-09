package io.github.mridang.codegen.spec;

import org.testcontainers.utility.DockerImageName;

public interface LanguageSpec {

  String getGeneratorName();

  DockerImageName getRuntimeImage();
}
