package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the C# quickstart, runs it against the mock server and compiles every {@code csharp}
 * fence of the README and SKILLS files. The fences are files of one project, {@code
 * .out/fences/Fences.csproj}, which references the SDK project and builds with nullable
 * reference types, implicit usings and warnings as errors. Each fence gets a namespace of its
 * own, so the names it declares never clash: a {@code file} fence has its {@code using}
 * directives hoisted above that namespace, a {@code statements} fence has them hoisted too and
 * the rest placed in one static method, which is {@code async} when the fence awaits. The working
 * copy is restored before every command batch, so the files never reach the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
public class CSharpExamplesSpec extends AbstractExamplesSpec implements CSharpSpec {

  /** A using directive (namespace, static or alias), never a using declaration or statement. */
  private static final Pattern USING_DIRECTIVE =
      Pattern.compile("^using (static )?[A-Za-z_][A-Za-z0-9_.]*( = [A-Za-z_][A-Za-z0-9_.<>, ]*)?;$");

  @Override
  protected String getFenceLanguage() {
    return "csharp";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    files.add(new HarnessFile(".out/fences/Fences.csproj", project()));
    for (Fence fence : fences) {
      String name = fenceName(fence);
      StringBuilder usings = new StringBuilder();
      StringBuilder code = new StringBuilder();
      boolean statements = fence.kind().equals("statements");
      for (String line : fence.body().split("\n", -1)) {
        if (USING_DIRECTIVE.matcher(line).matches()) {
          usings.append(line).append('\n');
        } else if (statements) {
          code.append(line.isEmpty() ? "" : "        ").append(line).append('\n');
        } else {
          code.append(line).append('\n');
        }
      }
      String header = usings + "\nnamespace Fences." + name + ";\n\n";
      if (!statements) {
        files.add(new HarnessFile(".out/fences/" + name + ".cs", header + code));
        continue;
      }
      String method =
          fence.body().contains("await ")
              ? "    internal static async Task RunAsync()\n"
              : "    internal static void Run()\n";
      files.add(
          new HarnessFile(
              ".out/fences/" + name + ".cs",
              header
                  + "internal static class Fence\n{\n"
                  + method
                  + "    {\n"
                  + code
                  + "    }\n}\n"));
    }
    return files;
  }

  private String project() {
    String pkg = String.valueOf(getCodegenProperties().get("packageName"));
    return "<Project Sdk=\"Microsoft.NET.Sdk\">\n"
        + "  <PropertyGroup>\n"
        + "    <TargetFramework>net10.0</TargetFramework>\n"
        + "    <LangVersion>14</LangVersion>\n"
        + "    <Nullable>enable</Nullable>\n"
        + "    <ImplicitUsings>enable</ImplicitUsings>\n"
        + "    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>\n"
        + "  </PropertyGroup>\n"
        + "  <ItemGroup>\n"
        + "    <ProjectReference Include=\"../../src/" + pkg + "/" + pkg + ".csproj\" />\n"
        + "  </ItemGroup>\n"
        + "</Project>\n";
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"dotnet build .out/fences/Fences.csproj"};
  }
}
