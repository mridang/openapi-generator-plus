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
    return DockerImageName.parse("mcr.microsoft.com/dotnet/sdk:10.0-alpine");
  }

  @Override
  default String getDockerImage() {
    return "mcr.microsoft.com/dotnet/sdk:10.0-alpine";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("packageName", "PetstoreClient", "sourceFolder", "src");
  }

  @Override
  default List<String> getSetupCommands() {
    /* The SDK image is Alpine and ships without make, which the SDK's own
     * docs target needs. Installed once with the shared container, with
     * retries because the package mirror is flaky on CI runners. */
    return List.of(
        "for i in 1 2 3 4 5; do apk add --no-cache make && break;"
            + " echo \"apk attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " command -v make > /dev/null"
            + " || { echo \"make missing after retries\" >&2; exit 1; }",
        "dotnet restore",
        /* Fetches the pinned docfx and csharpier from the local tool manifest
         * into the shared package cache, so the docs target does not download
         * docfx during the test. */
        "dotnet tool restore");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* NUGET_PACKAGES relocates the global package cache; .nuget/packages
     * subtree survives across CSharpBuildSpec → CSharpClientSpec →
     * CSharpStaticAnalysisSpec. Project bin/ and obj/ stay local (would
     * need MSBuild BaseOutputPath property to relocate). */
    return Map.of(
        "NUGET_PACKAGES", "/root/.cache/csharp/nuget",
        /* Skip the first-run experience and telemetry round trips. */
        "DOTNET_CLI_TELEMETRY_OPTOUT", "1",
        "DOTNET_SKIP_FIRST_TIME_EXPERIENCE", "1",
        "DOTNET_NOLOGO", "1");
  }
}
