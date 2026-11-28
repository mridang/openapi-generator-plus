package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated C# SDK (D180): the SDK assembly is built and a small
 * reflection program, run as a .NET file-based app, lists every exported type and its public and
 * protected members, one signature per line with nullable annotations. If this test fails on the
 * snapshot, the public API changed; if it fails on a leak, a public signature names a type of a
 * library outside the .NET base class library.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
public class CSharpApiSurfaceSpec extends AbstractApiSurfaceSpec implements CSharpSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("csharp-surface.cs");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {
      "dotnet build src/PetstoreClient/PetstoreClient.csproj -o /tmp/api-surface-bin -v q",
      "dotnet run --file /tmp/api-surface/csharp-surface.cs"
          + " -- /tmp/api-surface-bin/PetstoreClient.dll"
    };
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    /* The SDK depends on the base class library only (System.Text.Json, HttpClient,
     * System.Diagnostics); these are the libraries a C# client would otherwise pull in. */
    return List.of(
        Pattern.compile("\\bNewtonsoft\\."),
        Pattern.compile("\\bRestSharp\\."),
        Pattern.compile("\\bPolly\\."),
        Pattern.compile("\\bMicrosoft\\.Extensions\\."),
        Pattern.compile("\\bOpenTelemetry\\."),
        Pattern.compile("\\bSerilog\\."),
        Pattern.compile("\\bNodaTime\\."));
  }
}
