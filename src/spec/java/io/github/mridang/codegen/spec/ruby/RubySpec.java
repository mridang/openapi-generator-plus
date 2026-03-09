package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface RubySpec extends LanguageSpec, DockerImageSpec {

  default String getGeneratorName() {
    return "ruby-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.4");
  }

  default String getDockerImage() {
    return "ruby:3.4-slim";
  }
}
