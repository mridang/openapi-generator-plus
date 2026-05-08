package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.LanguageSpec;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("dart")
interface DartSpec extends LanguageSpec {

  @Override
  default String getGeneratorName() {
    return "dart-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("dart:stable");
  }
}
