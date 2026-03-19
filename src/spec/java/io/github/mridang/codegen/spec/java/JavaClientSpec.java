package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
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
  }
}
