package io.github.mridang.codegen.spec.php;

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
public class PhpTlsProxySpec extends AbstractTlsProxySpec {

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
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem vendor/bin/phpunit --testdox tests/DefaultApiClientTest.php"
    };
  }

  @BeforeEach
  void setup() throws IOException {
    copyTestProject(Paths.get("src/spec/resources/testprojects/phptest"));
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("PHP TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }
}
