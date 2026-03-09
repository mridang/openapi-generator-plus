package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpTlsProxySpec extends AbstractTlsProxySpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "composer install --no-interaction --prefer-dist",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem vendor/bin/phpunit --testdox tests/DefaultApiClientTest.php"
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
}
