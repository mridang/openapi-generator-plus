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
    // Run the xunit.v3 test executable directly (Microsoft Testing Platform,
    // no VSTest). VSTest via `dotnet test` silently skipped the container-
    // backed collection specs; the xunit.v3 runner executes every test and
    // writes the JUnit report the shared harness reads.
    return new String[] {
      "dotnet run --project PetstoreClient.Test.csproj -- -jUnit .out/reports/junit.xml"
    };
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/PetstoreClient/Api")).exists();
    assertThat(outputDir.resolve("src/PetstoreClient/Models")).exists();
  }
}
