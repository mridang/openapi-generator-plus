package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaClientSpec extends AbstractClientSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile test-compile -q -B",
      "mvn test -q -B",
      "mv .out/jacoco.xml .out/coverage.xml"
    };
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/main/java/com/example/petstore/api")).exists();
    assertThat(outputDir.resolve("src/main/java/com/example/petstore/models")).exists();
    deprecatedParamStatusIsMarkedDeprecatedInOptions(outputDir);
  }

  /**
   * The findPetsByStatus operation declares its {@code status} query parameter as
   * {@code deprecated: true} in the spec. The generated per-operation Options class must
   * propagate that deprecation: the {@code status} field and its accessors carry the
   * {@code @Deprecated} annotation so callers see the deprecation warning, mirroring the
   * model-property deprecation idiom.
   */
  private void deprecatedParamStatusIsMarkedDeprecatedInOptions(Path outputDir) {
    Path options =
        outputDir.resolve(
            "src/main/java/com/example/petstore/api/options/FindPetsByStatusOptions.java");
    assertThat(options).exists();
    assertThatNoException()
        .isThrownBy(
            () -> {
              String source = Files.readString(options);
              // The status accessor stays a plain String (allowEmptyValue:true), so the
              // only change is the added deprecation marker on the status member.
              assertThat(source).contains("@Deprecated");
              assertThat(source).contains("public String status()");
            });
  }
}
