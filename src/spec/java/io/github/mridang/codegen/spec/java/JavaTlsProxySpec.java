package io.github.mridang.codegen.spec.java;

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
public class JavaTlsProxySpec extends AbstractTlsProxySpec {

  private static final String PACKAGE_NAME = "com.example.petstore";

  @Override
  protected String getGeneratorName() {
    return "java-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("maven:3.9-eclipse-temurin-17");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile test-compile -q -B",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem mvn test -q -B -Dtest=DefaultApiClientTest"
    };
  }

  @BeforeEach
  void setup() throws IOException {
    copyTestProject(Paths.get("src/spec/resources/testprojects/javatest"));
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(
        Map.of(
            CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
            CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
            CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Java TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }
}
