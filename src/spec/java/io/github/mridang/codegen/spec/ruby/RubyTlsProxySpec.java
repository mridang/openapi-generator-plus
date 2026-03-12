package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyTlsProxySpec extends AbstractTlsProxySpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "bundle install --quiet",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem bundle exec ruby -Ilib -Ispec spec/default_api_client_spec.rb"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/rubytest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.GEM_NAME, "opigen_client",
        CodegenConstants.MODULE_NAME, "OpigenClient");
  }
}
