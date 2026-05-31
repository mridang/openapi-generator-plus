package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpClientSpec extends AbstractClientSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mkdir -p .out && vendor/bin/pest test --testdox --parallel"};
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("lib/Api")).exists();
    assertThat(outputDir.resolve("lib/Models")).exists();
  }
}
