package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Ruby code passes Steep type checking with RBS signatures. If this test
 * fails, the Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyTypeCheckSpec extends AbstractIntegrationSpec {

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
      "apt-get update && apt-get install -y build-essential --no-install-recommends 2>/dev/null",
      "gem install steep --no-document",
      "mkdir -p sig"
          + " && find lib/opigen_client/models lib/opigen_client/api"
          + " -name '*.rb' ! -name 'base_api.rb'"
          + " -exec rbs prototype rb {} + > sig/generated.rbs",
      "steep check"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldPassTypeChecking() throws IOException {
    generateClientToDirectory(
        Map.of("gemName", "opigen_client", "moduleName", "OpigenClient"), tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve("Steepfile"),
        String.join(
            "\n",
            "target :lib do",
            "  check \"lib\"",
            "  signature \"sig\"",
            "  library \"date\", \"json\", \"time\", \"cgi\", \"uri\"",
            "end",
            ""));

    Files.createDirectories(tempOutputDir.resolve("sig"));
    writeVendorStubs(tempOutputDir.resolve("sig/vendor.rbs"));
    writeInfrastructureRbs(tempOutputDir.resolve("sig/infrastructure.rbs"));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has type checking errors:\n%s", result.output())
        .isTrue();
  }

  private void writeVendorStubs(Path path) throws IOException {
    Files.writeString(
        path,
        String.join(
            "\n",
            "module Dry",
            "  def self.Types: () -> Module",
            "",
            "  class Struct",
            "    def self.attribute: (Symbol, untyped) -> void",
            "    def self.transform_keys: () { (untyped) -> untyped } -> void",
            "  end",
            "end",
            "",
            "module Types",
            "  Any: untyped",
            "end",
            "",
            "module Typhoeus",
            "  class Request",
            "    def initialize: (String, Hash[Symbol, untyped]) -> void",
            "    def run: () -> Response",
            "  end",
            "",
            "  class Response",
            "    def code: () -> Integer",
            "    def body: () -> String",
            "    def headers: () -> (Hash[String, String] | nil)",
            "  end",
            "end",
            ""));
  }

  @SuppressWarnings("StringBufferReplaceableByString")
  private void writeInfrastructureRbs(Path path) throws IOException {
    StringBuilder rbs = new StringBuilder();

    rbs.append("module OpigenClient\n");
    rbs.append("  def self.configure: () { (Configuration) -> void } -> void\n");
    rbs.append("                    | () -> Configuration\n\n");

    // VERSION
    rbs.append("  VERSION: String\n\n");

    // Configuration
    rbs.append("  class Configuration\n");
    rbs.append("    attr_accessor base_url: String\n");
    rbs.append("    attr_accessor default_headers: Hash[String, String]\n");
    rbs.append("    attr_accessor debug: bool\n");
    rbs.append("    attr_accessor verify_ssl: bool\n");
    rbs.append("    attr_accessor ssl_ca_cert: String?\n");
    rbs.append("    attr_accessor cert_file: String?\n");
    rbs.append("    attr_accessor key_file: String?\n");
    rbs.append("    attr_accessor proxy: String?\n");
    rbs.append("    attr_accessor timeout: Integer?\n");
    rbs.append("    attr_accessor retries: Integer?\n");
    rbs.append("    def initialize: () -> void\n");
    rbs.append("                  | () { (Configuration) -> void } -> void\n");
    rbs.append("    def self.default: () -> Configuration\n");
    rbs.append("    def self.default=: (Configuration) -> Configuration\n");
    rbs.append("  end\n\n");

    // ApiResponse
    rbs.append("  class ApiResponse\n");
    rbs.append("    attr_reader status_code: Integer\n");
    rbs.append("    attr_reader body: String\n");
    rbs.append("    attr_reader headers: Hash[String, String]\n");
    rbs.append("    def initialize: (status_code: Integer, body: String,");
    rbs.append(" headers: Hash[String, String]) -> void\n");
    rbs.append("  end\n\n");

    // ApiError
    rbs.append("  class ApiError < StandardError\n");
    rbs.append("    attr_reader code: Integer?\n");
    rbs.append("    attr_reader response_headers: Hash[String, String]?\n");
    rbs.append("    attr_reader response_body: String?\n");
    rbs.append("    def initialize: (?untyped) -> void\n");
    rbs.append("    def message: () -> String\n");
    rbs.append("  end\n\n");

    // ApiClient
    rbs.append("  class ApiClient\n");
    rbs.append("    def send_request: (Symbol, String,");
    rbs.append(" Hash[String, String], untyped) -> ApiResponse\n");
    rbs.append("  end\n\n");

    // DefaultApiClient
    rbs.append("  class DefaultApiClient < ApiClient\n");
    rbs.append("    def initialize: (?Configuration?) -> void\n");
    rbs.append("    def send_request: (Symbol, String,");
    rbs.append(" Hash[String, String], untyped) -> ApiResponse\n");
    rbs.append("  end\n\n");

    // SerializationError
    rbs.append("  class SerializationError < StandardError\n");
    rbs.append("    attr_reader cause: Exception?\n");
    rbs.append("    def initialize: (String, ?Exception?) -> void\n");
    rbs.append("  end\n\n");

    // ObjectSerializer - all methods including private
    rbs.append("  class ObjectSerializer\n");
    rbs.append("    DEFAULT_DATETIME_FORMAT: String\n");
    rbs.append("    def self.serialize: (untyped) -> String\n");
    rbs.append("    def self.deserialize: (untyped, String) -> untyped\n");
    rbs.append("    def self.to_path_value: (untyped) -> String\n");
    rbs.append("    def self.to_query_value: (untyped, ?Symbol?) -> untyped\n");
    rbs.append("    def self.to_header_value: (untyped) -> String\n");
    rbs.append("    def self.to_form_value: (untyped) -> String\n");
    rbs.append("    def self.convert_to_type: (untyped, String) -> untyped\n");
    rbs.append("    def self.find_and_cast_into_type: (untyped, untyped) -> untyped\n");
    rbs.append("    def self.sanitize_for_serialization: (untyped) -> untyped\n");
    rbs.append("    def self.deserialize_model: (untyped, untyped) -> untyped\n");
    rbs.append("    SchemaMismatchError: singleton(StandardError)\n");
    rbs.append("  end\n\n");

    // HeaderSelector - all methods including private
    rbs.append("  class HeaderSelector\n");
    rbs.append("    JSON_MIME_PATTERN: Regexp\n");
    rbs.append("    WEIGHT_PATTERN: Regexp\n");
    rbs.append("    HeaderData: untyped\n");
    rbs.append("    def select_headers: (Array[String], String, bool)");
    rbs.append(" -> Hash[String, String]\n");
    rbs.append("    def json_mime?: (String?) -> bool\n");
    rbs.append("    def get_next_weight: (Integer, bool) -> Integer\n");
    rbs.append("    private\n");
    rbs.append("    def select_accept_header: (Array[String]?) -> String?\n");
    rbs.append("    def select_json_mime_list: (Array[String]) -> Array[String]\n");
    rbs.append("    def get_accept_header_with_adjusted_weight:");
    rbs.append(" (Array[String], Array[String]) -> String\n");
    rbs.append("    def get_header_and_weight: (String) -> untyped\n");
    rbs.append("    def adjust_weight: (Array[untyped], Array[Integer], bool)");
    rbs.append(" -> Array[String]\n");
    rbs.append("    def build_accept_header: (String, Integer) -> String\n");
    rbs.append("  end\n\n");

    // Models module
    rbs.append("  module Models\n");
    rbs.append("  end\n\n");

    // Api module with BaseApi
    rbs.append("  module Api\n");
    rbs.append("    class BaseApi\n");
    rbs.append("      attr_reader api_client: ApiClient\n");
    rbs.append("      attr_reader config: Configuration\n");
    rbs.append("      attr_reader header_selector: HeaderSelector\n");
    rbs.append("      def initialize: (?ApiClient?, ?Configuration) -> void\n");
    rbs.append("      def invoke_api: (Symbol, String, Hash[String, untyped],");
    rbs.append(" Hash[String, String], untyped, Array[String], String?, String?)");
    rbs.append(" -> untyped\n");
    rbs.append("      def build_collection_param: (Array[untyped], Symbol) -> untyped\n");
    rbs.append("      private\n");
    rbs.append("      def build_query_string: (Hash[String, untyped]) -> String\n");
    rbs.append("      def serialize_body: (untyped, String?) -> untyped\n");
    rbs.append("    end\n");
    rbs.append("  end\n");

    rbs.append("end\n");

    Files.writeString(path, rbs.toString());
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator =
        new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}
