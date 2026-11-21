package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Rust quickstart, runs it against the mock server and lints every {@code rust} fence
 * of the README and SKILLS files. The fences become one cargo example target, {@code
 * examples/fences}, with one module per fence: a {@code file} fence is the module body as
 * written, a {@code statements} fence is placed in an {@code async} function that returns a boxed
 * error, so {@code ?} and {@code .await} are legal in it and its {@code use} declarations stay
 * where they are (Rust allows them in a function body). The target is checked with clippy and
 * {@code -D warnings}, the gate {@code make lint} applies to the examples, under the crate's own
 * {@code [lints]}. The working copy is restored before every command batch, so the files never
 * reach the golden.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustExamplesSpec extends AbstractExamplesSpec implements RustSpec {

  @Override
  protected String getFenceLanguage() {
    return "rust";
  }

  @Override
  protected List<HarnessFile> wrap(List<Fence> fences) {
    List<HarnessFile> files = new ArrayList<>();
    StringBuilder main = new StringBuilder();
    for (Fence fence : fences) {
      String name = fenceName(fence);
      main.append("mod ").append(name).append(";\n");
      String path = "examples/fences/" + name + ".rs";
      if (fence.kind().equals("file")) {
        files.add(new HarnessFile(path, fence.body()));
        continue;
      }
      files.add(
          new HarnessFile(
              path,
              "pub async fn fence() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {\n"
                  + fence.body()
                  + "    Ok(())\n}\n"));
    }
    files.add(new HarnessFile("examples/fences/main.rs", main + "\nfn main() {}\n"));
    return files;
  }

  @Override
  protected String[] getFenceBuildCommands() {
    return new String[] {"CARGO_BUILD_JOBS=2 cargo clippy --example fences --all-features -- -D warnings"};
  }

  @Override
  protected String[] getExamplesBuildCommands() {
    return new String[] {"CARGO_BUILD_JOBS=2 make examples"};
  }

  @Override
  protected String[] getExamplesRunCommands() {
    return new String[] {"CARGO_BUILD_JOBS=2 make examples-run"};
  }
}
