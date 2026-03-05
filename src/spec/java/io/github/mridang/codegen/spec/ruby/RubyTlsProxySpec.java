package io.github.mridang.codegen.spec.ruby;

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
public class RubyTlsProxySpec extends AbstractTlsProxySpec {

  private static final String MODULE_NAME = "OpigenClient";
  private static final String GEM_NAME = "opigen_client";

  @Override
  protected String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.2-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apt-get update && apt-get install -y libcurl4 build-essential --no-install-recommends 2>/dev/null",
      "bundle install --quiet",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem bundle exec rspec spec/default_api_client_spec.rb --format documentation"
    };
  }

  @BeforeEach
  void setup() throws IOException {
    copyTestProject(Paths.get("src/spec/resources/testprojects/rspec"));
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(
        Map.of(
            CodegenConstants.GEM_NAME, GEM_NAME,
            CodegenConstants.MODULE_NAME, MODULE_NAME),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Ruby TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }
}
