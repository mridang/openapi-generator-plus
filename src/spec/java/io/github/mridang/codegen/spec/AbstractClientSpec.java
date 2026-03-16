package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Base class for client integration specs. Provides the shared test structure:
 * copy test project, generate client, assert structure, then run tests against Prism.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractClientSpec extends AbstractIntegrationSpec {

  protected abstract Path getTestProjectPath();

  protected abstract Map<String, Object> getCodegenProperties();

  protected abstract void assertGeneratedStructure(Path outputDir);

  @BeforeEach
  void copyTestProject() throws IOException {
    copyDirectory(getTestProjectPath(), tempOutputDir);

    // Copy shared test resources so language-native Testcontainers can access them
    copyClasspathResource("specs/petstore/openapi.yaml", tempOutputDir.resolve("specs/openapi.yaml"));
    copyClasspathResource("certs/ca.pem", tempOutputDir.resolve("certs/ca.pem"));
    copyClasspathResource("certs/ca-key.pem", tempOutputDir.resolve("certs/ca-key.pem"));
    copyClasspathResource("certs/server.pem", tempOutputDir.resolve("certs/server.pem"));
    copyClasspathResource("certs/server-key.pem", tempOutputDir.resolve("certs/server-key.pem"));
    copyClasspathResource("certs/server-keystore.p12", tempOutputDir.resolve("certs/server-keystore.p12"));
    copyClasspathResource("wiremock/mappings/test.json", tempOutputDir.resolve("wiremock/mappings/test.json"));
    copyClasspathResource("proxy/squid.conf", tempOutputDir.resolve("proxy/squid.conf"));
  }

  @Test
  @Order(1)
  void shouldGenerateClient() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);
    assertGeneratedStructure(tempOutputDir);
  }

  @Test
  @Order(2)
  void shouldRunClientTests() throws IOException {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    // Re-copy test project overlay to restore dependency manifests and test configs
    // that were overwritten by the code generator (pom.xml, package.json, etc.)
    copyDirectory(getTestProjectPath(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Client tests failed:\n%s", result.output())
        .isTrue();
  }
}
