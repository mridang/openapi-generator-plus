package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaTlsProxySpec extends AbstractTlsProxySpec implements JavaSpec {

  private static final String PACKAGE_NAME = "com.example.petstore";

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile test-compile -q -B",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem mvn test -q -B -Dtest=DefaultApiClientTest"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/javatest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
        CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
        CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME);
  }
}
