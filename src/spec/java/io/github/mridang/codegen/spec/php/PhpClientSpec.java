package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpClientSpec extends AbstractClientSpec {

  @Override
  protected String getGeneratorName() {
    return "php-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("composer:2");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "composer install --no-interaction --prefer-dist",
      "API_BASE_URL=http://prism:4010 vendor/bin/phpunit --testdox"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/phptest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient");
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("lib/Api")).exists();
    assertThat(outputDir.resolve("lib/Models")).exists();
  }
}
