package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PythonTlsProxySpec extends AbstractTlsProxySpec {

  @Override
  protected String getGeneratorName() {
    return "python-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("python:3.11-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "pip install --quiet -r requirements.txt",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem python -m pytest tests/test_default_api_client.py -v"
    };
  }

  @BeforeEach
  void setup() throws IOException {
    copyTestProject(Paths.get("src/spec/resources/testprojects/pytest"));
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "petstore_client",
            CodegenConstants.PROJECT_NAME, "petstore-client"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Python TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }
}
