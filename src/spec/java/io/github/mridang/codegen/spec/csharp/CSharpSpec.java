package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface CSharpSpec extends LanguageSpec, DockerImageSpec {

  default String getGeneratorName() {
    return "csharp-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("mcr.microsoft.com/dotnet/sdk:9.0");
  }

  default String getDockerImage() {
    return "mcr.microsoft.com/dotnet/sdk:9.0";
  }
}
