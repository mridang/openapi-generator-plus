package io.github.mridang.codegen.spec.node;

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
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NodeTlsProxySpec extends AbstractTlsProxySpec {

  @Override
  protected String getGeneratorName() {
    return "node-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:20-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem npx jest tests/default-api-client.test.ts --verbose"
    };
  }

  @BeforeEach
  void setup() throws IOException {
    copyTestProject(Paths.get("src/spec/resources/testprojects/nodetest"));
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(Map.of(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Node TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }
}
