package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PythonClientSpec extends AbstractClientSpec implements PythonSpec {

  private static final String PACKAGE_NAME = "petstore_client";

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "pip install --quiet -r requirements.txt",
      "python -m pytest tests/ -v"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/pytest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
        CodegenConstants.PROJECT_NAME, "petstore-client");
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve(PACKAGE_NAME)).exists();
    assertThat(outputDir.resolve(PACKAGE_NAME + "/api")).exists();
    assertThat(outputDir.resolve(PACKAGE_NAME + "/models")).exists();
  }
}
