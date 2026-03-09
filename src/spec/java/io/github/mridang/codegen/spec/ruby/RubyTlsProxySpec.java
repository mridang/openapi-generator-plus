package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractTlsProxySpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyTlsProxySpec extends AbstractTlsProxySpec {

  @Override
  protected String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.4-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apt-get update && apt-get install -y libcurl4 build-essential --no-install-recommends 2>/dev/null",
      "bundle install --quiet",
      "WIREMOCK_HTTPS_URL=https://wiremock:8443 WIREMOCK_HTTP_URL=http://wiremock:8080 PROXY_URL=http://proxy:3128 CA_CERT_PATH=/app/ca.pem bundle exec rspec spec/default_api_client_spec.rb --format documentation"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/rspec");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.GEM_NAME, "opigen_client",
        CodegenConstants.MODULE_NAME, "OpigenClient");
  }
}
