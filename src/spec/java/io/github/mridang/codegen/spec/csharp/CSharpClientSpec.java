package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpClientSpec extends AbstractClientSpec implements CSharpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "dotnet restore",
      "dotnet test --verbosity normal"
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
