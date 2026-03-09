package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpClientSpec extends AbstractClientSpec {

  @Override
  protected String getGeneratorName() {
    return "csharp-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("mcr.microsoft.com/dotnet/sdk:9.0");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "dotnet restore",
      "API_BASE_URL=http://prism:4010 dotnet test --verbosity normal"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/csharptest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.PACKAGE_NAME, "PetstoreClient",
        CodegenConstants.SOURCE_FOLDER, "src");
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/PetstoreClient/Api")).exists();
    assertThat(outputDir.resolve("src/PetstoreClient/Models")).exists();
  }
}
