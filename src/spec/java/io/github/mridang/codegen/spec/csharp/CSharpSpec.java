package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("csharp")
interface CSharpSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "csharp-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("mcr.microsoft.com/dotnet/sdk:10.0");
  }

  @Override
  default String getDockerImage() {
    return "mcr.microsoft.com/dotnet/sdk:10.0";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("packageName", "PetstoreClient", "sourceFolder", "src");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("dotnet restore");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* NUGET_PACKAGES relocates the global package cache; .nuget/packages
     * subtree survives across CSharpBuildSpec → CSharpClientSpec →
     * CSharpStaticAnalysisSpec. Project bin/ and obj/ stay local (would
     * need MSBuild BaseOutputPath property to relocate). */
    return Map.of("NUGET_PACKAGES", "/root/.cache/csharp/nuget");
  }
}
