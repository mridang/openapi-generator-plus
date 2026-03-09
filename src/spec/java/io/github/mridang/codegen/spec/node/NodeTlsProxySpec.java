package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeTlsProxySpec extends AbstractTlsProxySpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem npx jest tests/default-api-client.test.ts --verbose"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/nodetest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of();
  }
}
