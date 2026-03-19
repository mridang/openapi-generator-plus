package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated C# code passes Roslyn static analysis. Uses dotnet build with warnings as
 * errors and Microsoft.CodeAnalysis.NetAnalyzers. If this test fails, the C# templates need to be
 * fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpStaticAnalysisSpec extends AbstractIntegrationSpec implements CSharpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"dotnet build src/PetstoreClient/PetstoreClient.csproj --warnaserror"};
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# code has static analysis violations:\n%s", result.output())
        .isTrue();
  }
}
