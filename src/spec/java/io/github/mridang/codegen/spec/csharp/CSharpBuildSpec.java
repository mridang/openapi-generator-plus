package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated C# code compiles cleanly with the strictest .NET SDK settings. Uses
 * TreatWarningsAsErrors, AnalysisLevel=latest-all, EnforceCodeStyleInBuild, and Nullable=enable. If
 * this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpBuildSpec extends AbstractIntegrationSpec implements CSharpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"dotnet build src/PetstoreClient/PetstoreClient.csproj"};
  }

  @Test
  void generatedCodeShouldCompileWithStrictSettings() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# code has build warnings/errors with strict settings:\n%s",
            result.output())
        .isTrue();
  }
}
