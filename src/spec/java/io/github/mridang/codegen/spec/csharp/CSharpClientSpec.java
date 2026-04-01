package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
public class CSharpClientSpec extends AbstractClientSpec implements CSharpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "dotnet restore",
      "dotnet test --verbosity normal"
    };
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/PetstoreClient/Api")).exists();
    assertThat(outputDir.resolve("src/PetstoreClient/Models")).exists();
  }
}
