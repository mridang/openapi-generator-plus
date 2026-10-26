package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the generated C# SDK works when a consuming application is published
 * with {@code PublishTrimmed} (Release Blazor WebAssembly, single-file, etc.).
 *
 * <p>Trimming is a runtime hazard the compiler cannot catch: the SDK serializes
 * through System.Text.Json's reflection-based resolver, so a trimmed consumer
 * would either crash with "reflection-based serialization has been disabled" or
 * silently lose model members. Two SDK-side mechanisms make it safe — the
 * pinned {@code DefaultJsonTypeInfoResolver} in {@code ObjectSerializer} and the
 * embedded {@code ILLink.Descriptors.xml} keep-list — and the models deserialize
 * through a parameterless constructor plus {@code required} init accessors so
 * ILLink has no constructor parameter names to strip.
 *
 * <p>The check publishes a tiny consumer (named {@code PetstoreClient.Test} so
 * the SDK's {@code InternalsVisibleTo} grant exposes {@code ObjectSerializer})
 * with {@code TrimMode=full}, then runs it to prove a {@code Pet} round-trips,
 * the backward-compatible convenience constructor still works, and the
 * required-field null-guard still hard-fails — all after trimming.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
public class CSharpTrimmedSpec extends AbstractIntegrationSpec implements CSharpSpec {

  /** Base64 of the consumer .csproj (PublishTrimmed, references the SDK). */
  private static final String CSPROJ_B64 =
      "PFByb2plY3QgU2RrPSJNaWNyb3NvZnQuTkVULlNkayI+CiAgPFByb3BlcnR5R3JvdXA+CiAgICA8T3V0cHV0VHlwZT5FeGU8L091dHB1dFR5cGU+CiAgICA8VGFyZ2V0RnJhbWV3b3JrPm5ldDguMDwvVGFyZ2V0RnJhbWV3b3JrPgogICAgPE51bGxhYmxlPmVuYWJsZTwvTnVsbGFibGU+CiAgICA8SW1wbGljaXRVc2luZ3M+ZW5hYmxlPC9JbXBsaWNpdFVzaW5ncz4KICAgIDxBc3NlbWJseU5hbWU+UGV0c3RvcmVDbGllbnQuVGVzdDwvQXNzZW1ibHlOYW1lPgogICAgPFB1Ymxpc2hUcmltbWVkPnRydWU8L1B1Ymxpc2hUcmltbWVkPgogICAgPFRyaW1Nb2RlPmZ1bGw8L1RyaW1Nb2RlPgogIDwvUHJvcGVydHlHcm91cD4KICA8SXRlbUdyb3VwPgogICAgPFByb2plY3RSZWZlcmVuY2UgSW5jbHVkZT0iL3dvcmsvc3JjL1BldHN0b3JlQ2xpZW50L1BldHN0b3JlQ2xpZW50LmNzcHJvaiIgLz4KICA8L0l0ZW1Hcm91cD4KPC9Qcm9qZWN0Pgo=";

  /** Base64 of the consumer Program.cs (round-trips a Pet via ObjectSerializer). */
  private static final String PROGRAM_B64 =
      "dXNpbmcgUGV0c3RvcmVDbGllbnQ7CnVzaW5nIFBldHN0b3JlQ2xpZW50Lk1vZGVsczsKCi8vIERyaXZlcyB0aGUgU0RLJ3MgaW50ZXJuYWwgT2JqZWN0U2VyaWFsaXplciAocmVmbGVjdGlvbi1iYXNlZCByZXNvbHZlciArIGV2ZXJ5Ci8vIGN1c3RvbSBjb252ZXJ0ZXIpIGFnYWluc3QgdGhlIHJlYWwgZ2VuZXJhdGVkIG1vZGVscywgdW5kZXIgZnVsbCB0cmltbWluZy4KdmFyIHNlcmlhbGl6ZXIgPSBuZXcgT2JqZWN0U2VyaWFsaXplcigpOwoKUGV0PyBwZXQgPSBzZXJpYWxpemVyLkRlc2VyaWFsaXplPFBldD4oCiAgICAie1wibmFtZVwiOlwiUmV4XCIsXCJwaG90b1VybHNcIjpbXCJodHRwOi8veC95LnBuZ1wiXSxcImlkXCI6N30iKTsKaWYgKHBldCBpcyBudWxsIHx8IHBldC5OYW1lICE9ICJSZXgiIHx8IHBldC5JZCAhPSA3IHx8IHBldC5QaG90b1VybHMuQ291bnQgIT0gMSkKewogICAgQ29uc29sZS5Xcml0ZUxpbmUoJCJGQUlMOiBkZXNlcmlhbGl6ZSBtaXNtYXRjaCIpOwogICAgcmV0dXJuIDE7Cn0KCnN0cmluZyBiYWNrID0gc2VyaWFsaXplci5TZXJpYWxpemUocGV0KTsKaWYgKCFiYWNrLkNvbnRhaW5zKCJcIm5hbWVcIjpcIlJleFwiIikgfHwgIWJhY2suQ29udGFpbnMoIlwicGhvdG9VcmxzXCIiKSkKewogICAgQ29uc29sZS5Xcml0ZUxpbmUoJCJGQUlMOiBzZXJpYWxpemUgbWlzbWF0Y2g6IHtiYWNrfSIpOwogICAgcmV0dXJuIDE7Cn0KCi8vIFRoZSBiYWNrd2FyZC1jb21wYXRpYmxlIGNvbnZlbmllbmNlIGNvbnN0cnVjdG9yIHN0aWxsIGNvbXBpbGVzIGFuZCBydW5zLgpQZXQgbWFkZSA9IG5ldygiQnVkZHkiLCBuZXcgSGFzaFNldDxzdHJpbmc+IHsgImh0dHA6Ly9hL2IucG5nIiB9KTsKXyA9IHNlcmlhbGl6ZXIuU2VyaWFsaXplKG1hZGUpOwoKLy8gRGl2ZXJnZW5jZSAjMTA6IGV4cGxpY2l0IG51bGwgb24gYSByZXF1aXJlZCBmaWVsZCBtdXN0IGhhcmQtZmFpbC4KdHJ5CnsKICAgIHNlcmlhbGl6ZXIuRGVzZXJpYWxpemU8UGV0Pigie1wibmFtZVwiOm51bGwsXCJwaG90b1VybHNcIjpbXX0iKTsKICAgIENvbnNvbGUuV3JpdGVMaW5lKCJGQUlMOiBudWxsLWd1YXJkIGRpZCBub3QgdGhyb3ciKTsKICAgIHJldHVybiAxOwp9CmNhdGNoIChFeGNlcHRpb24pCnsKICAgIC8vIGV4cGVjdGVkCn0KCkNvbnNvbGUuV3JpdGVMaW5lKCJST1VORFRSSVAgT0siKTsKcmV0dXJuIDA7Cg==";

  @Override
  protected String[] getBuildCommands() {
    // One shell command (newlines are fine under `sh -c`): materialise the
    // consumer, derive the alpine RID for whatever arch CI runs on, publish it
    // fully trimmed against the generated SDK, then run it. The consumer exits
    // non-zero on any assertion failure, so the publish + run exit code is the
    // pass/fail signal.
    String script =
        "mkdir -p /tmp/consumer"
            + " && echo '" + CSPROJ_B64 + "' | base64 -d > /tmp/consumer/Consumer.csproj"
            + " && echo '" + PROGRAM_B64 + "' | base64 -d > /tmp/consumer/Program.cs"
            + " && RID=linux-musl-$(uname -m | sed 's/x86_64/x64/;s/aarch64/arm64/')"
            + " && dotnet publish /tmp/consumer/Consumer.csproj -c Release -r \"$RID\""
            + " --self-contained -p:PublishTrimmed=true -p:TrimMode=full"
            + " && /tmp/consumer/bin/Release/net8.0/\"$RID\"/publish/PetstoreClient.Test";
    return new String[] {script};
  }

  @Test
  void generatedCodeShouldRoundTripWhenTrimmed() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# SDK fails to round-trip when published trimmed:\n%s", result.output())
        .isTrue();
    assertThat(result.output())
        .withFailMessage("Trimmed consumer did not complete the round-trip:\n%s", result.output())
        .contains("ROUNDTRIP OK");
  }
}
