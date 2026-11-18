package io.github.mridang.codegen.generators.rust;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenProperty;

class BetterRustCodegenTest {

  private final BetterRustCodegen codegen = new BetterRustCodegen();

  @Test
  void quotesAndEscapesAnApiKeyNameSoItCannotBreakTheLiteral() {
    assertThat(codegen.toRustStringLiteral("X-Api-Key")).isEqualTo("\"X-Api-Key\"");
    assertThat(codegen.toRustStringLiteral("X-Api\"Key\\")).isEqualTo("\"X-Api\\\"Key\\\\\"");
    assertThat(codegen.toRustStringLiteral("a\nb")).isEqualTo("\"a\\nb\"");
  }

  @Test
  void leavesAUrlUntouchedAndTurnsAnEmptyValueIntoAnEmptyLiteral() {
    assertThat(codegen.toRustStringLiteral("https://auth.example.com/oauth/token?a=b&c=d"))
        .isEqualTo("\"https://auth.example.com/oauth/token?a=b&c=d\"");
    assertThat(codegen.toRustStringLiteral("")).isEqualTo("\"\"");
  }

  @Test
  void aComponentDefaultOnAReferencedEnumNamesTheTypedMember() {
    final StringSchema availability = new StringSchema();
    availability.setEnum(List.of("Available", "on-hold"));
    availability.setDefault("on-hold");
    codegen.setOpenAPI(
        new OpenAPI().components(new Components().addSchemas("Availability", availability)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Availability");

    assertThat(codegen.toDefaultValue(reference)).isEqualTo("Availability::OnHold");
  }

  /* D60, F866: a default written next to a $ref reaches the property through the shared
   * siblingDefault data, and a referenced string enum names its declared member. */
  @Test
  void aSiblingDefaultOnAReferencedEnumNamesTheTypedMember() {
    final CodegenProperty property = propertyNamed("availability");
    property.vendorExtensions.put("siblingDefault", "on-hold");
    property.vendorExtensions.put("siblingDefaultIsEnum", true);
    property.vendorExtensions.put("siblingDefaultEnumType", "Availability");
    property.vendorExtensions.put("siblingDefaultEnumVar", "OnHold");

    codegen.seedSiblingDefault(property);

    assertThat(property.defaultValue).isEqualTo("Availability::OnHold");
  }

  @Test
  void aSiblingDefaultOnAReferencedStringTakesTheLiteralOfItsType() {
    final CodegenProperty property = propertyNamed("label");
    property.isString = true;
    property.vendorExtensions.put("siblingDefault", "a\"b");
    property.vendorExtensions.put("siblingDefaultIsEnum", false);

    codegen.seedSiblingDefault(property);

    assertThat(property.defaultValue).isEqualTo("String::from(\"a\\\"b\")");
  }

  @Test
  void aSiblingDefaultOnAReferencedNumberGetsADecimalPoint() {
    final CodegenProperty property = propertyNamed("ratio");
    property.isNumber = true;
    property.vendorExtensions.put("siblingDefault", "2");
    property.vendorExtensions.put("siblingDefaultIsEnum", false);

    codegen.seedSiblingDefault(property);

    assertThat(property.defaultValue).isEqualTo("2.0");
  }

  @Test
  void aPropertyWithoutASiblingDefaultKeepsItsOwnDefault() {
    final CodegenProperty property = propertyNamed("label");
    property.defaultValue = "String::from(\"x\")";

    codegen.seedSiblingDefault(property);

    assertThat(property.defaultValue).isEqualTo("String::from(\"x\")");
  }

  private static CodegenProperty propertyNamed(final String name) {
    final CodegenProperty property = new CodegenProperty();
    property.name = name;
    property.baseName = name;
    return property;
  }

  /* F827: every operation gets an Options struct carrying the per-call settings. */
  @Test
  void everyOperationMintsAnOptionsStruct() {
    assertThat(codegen.mintsOptionsForEveryOperation()).isTrue();
  }

  @Test
  void aSchemaNamedLikeAPreludeItemGetsTheModelPrefix() {
    final List<String> names =
        List.of("Result", "Option", "Vec", "Box", "Some", "None", "Ok", "Err");
    for (final String name : names) {
      assertThat(codegen.toModelName(name)).isEqualTo("Model" + name);
    }
    assertThat(codegen.toModelName("Order")).isEqualTo("Order");
    assertThat(codegen.toModelName("order_item")).isEqualTo("OrderItem");
  }

  @Test
  void aTypeThatNamesARenamedPreludeModelIsPointedAtTheRenamedModel() {
    assertThat(BetterRustCodegen.renamePreludeModels("Result")).isEqualTo("ModelResult");
    assertThat(BetterRustCodegen.renamePreludeModels("Vec<Result>")).isEqualTo("Vec<ModelResult>");
    assertThat(BetterRustCodegen.renamePreludeModels("HashMap<String, Result>"))
        .isEqualTo("HashMap<String, ModelResult>");
    assertThat(BetterRustCodegen.renamePreludeModels("Order")).isEqualTo("Order");
    assertThat(BetterRustCodegen.renamePreludeModels("ModelResult")).isEqualTo("ModelResult");
    assertThat(BetterRustCodegen.renamePreludeModels("Result<Order, Error>"))
        .isEqualTo("Result<Order, Error>");
    assertThat(BetterRustCodegen.renamePreludeModels(null)).isNull();
  }

  @Test
  void renamesAParameterNamedLikeAnInjectedArgumentOrALocal() {
    assertThat(codegen.toParamName("options")).isEqualTo("options_param");
    assertThat(codegen.toParamName("server")).isEqualTo("server_param");
    assertThat(codegen.toParamName("path")).isEqualTo("path_param");
    assertThat(codegen.toParamName("headers")).isEqualTo("headers_param");
    assertThat(codegen.toParamName("item_id")).isEqualTo("item_id");
  }

  /* F827: a parameter named like a per-call setting is renamed, so the options struct can carry
   * the timeout, the retry budget and the headers. */
  @Test
  void renamesAParameterNamedLikeAPerCallSetting() {
    assertThat(codegen.toParamName("timeout")).isEqualTo("timeout_param");
    assertThat(codegen.toParamName("max_retries")).isEqualTo("max_retries_param");
    assertThat(codegen.toParamName("header")).isEqualTo("header_param");
  }

  /* F308: an integer enum member takes the casing of every other Rust variant. */
  @Test
  void anIntegerEnumMemberIsCamelCased() {
    assertThat(codegen.toEnumVarName("2", "i32")).isEqualTo("Number2");
    assertThat(codegen.toEnumVarName("-1", "i64")).isEqualTo("NumberMinus1");
    assertThat(codegen.toEnumVarName("1.5", "f64")).isEqualTo("Number1Dot5");
    assertThat(codegen.toEnumVarName("active", "String")).isEqualTo("Active");
  }

  /* F1154: the server comes before the options in every operation signature. */
  @Test
  void theServerComesBeforeTheOptionsInEverySignature() throws IOException {
    final Path api = Path.of("src/spec/resources/generated/rust/src/api");
    final Pattern signature = Pattern.compile("pub async fn \\w+\\(\\n(.*?)\\n    \\)", Pattern.DOTALL);
    int checked = 0;
    for (final String file : new String[] {"pet_api.rs", "store_api.rs"}) {
      final Matcher m = signature.matcher(Files.readString(api.resolve(file)));
      while (m.find()) {
        final String args = m.group(1);
        final int server = args.indexOf("        server:");
        final int options = args.indexOf("        options:");
        if (server >= 0 && options >= 0) {
          assertThat(server).as("server must precede options in:\n" + args).isLessThan(options);
          checked++;
        }
      }
    }
    assertThat(checked).as("operations with both a server and options").isGreaterThan(0);
  }

  /* F827: every operation of the petstore golden takes the options argument that carries the
   * per-call timeout, retry budget and headers. */
  @Test
  void everyOperationTakesAnOptionsArgument() throws IOException {
    final Path api = Path.of("src/spec/resources/generated/rust/src/api");
    final Pattern signature =
        Pattern.compile("pub async fn (\\w+)\\(\\s*&self,(.*?)\\)\\s*->", Pattern.DOTALL);
    int checked = 0;
    for (final String file : new String[] {"pet_api.rs", "store_api.rs"}) {
      final Matcher m = signature.matcher(Files.readString(api.resolve(file)));
      while (m.find()) {
        assertThat(m.group(2)).as("signature of " + m.group(1)).contains("options:");
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(0);
  }

  /* F894 and F862: the package metadata options and the environment prefix reach the templates. */
  @Test
  void packageMetadataOptionsAndTheEnvironmentPrefixAreExposedToTheTemplates() {
    final BetterRustCodegen configured = new BetterRustCodegen();
    configured.additionalProperties().put("packageName", "item-client");
    configured.additionalProperties().put("repositoryUrl", "https://example.com/repo");
    configured.additionalProperties().put("authors", "Ada Lovelace, \"Q\" Smith,");
    configured.processOpts();

    assertThat(configured.additionalProperties().get("hasProjectUrls")).isEqualTo(true);
    assertThat(configured.additionalProperties().get("hasAuthors")).isEqualTo(true);
    assertThat(configured.additionalProperties().get("authorsList"))
        .isEqualTo(List.of(Map.of("name", "Ada Lovelace"), Map.of("name", "\\\"Q\\\" Smith")));
    assertThat(configured.additionalProperties().get("repositoryUrl"))
        .isEqualTo("https://example.com/repo");
    assertThat(configured.additionalProperties()).doesNotContainKey("homepageUrl");
    assertThat(configured.additionalProperties().get("envPrefix")).isEqualTo("ITEM_CLIENT");
  }

  /* A union names its members, so its file imports the sibling models with the glob. A subtype
   * that names no sibling, and a union of primitives, must not carry an unused glob, which fails
   * the -D warnings lint gate. */
  @Test
  void theSiblingGlobIsWrittenOnlyWhereAFileNamesASibling() throws IOException {
    final Path models = Path.of("src/spec/resources/generated/rust/src/models");
    for (final String union : new String[] {"shape.rs", "vehicle.rs"}) {
      assertThat(Files.readString(models.resolve(union)))
          .as(union)
          .contains("use super::*;");
    }
    for (final String subtype : new String[] {"car.rs", "circle.rs", "square.rs", "truck.rs"}) {
      assertThat(Files.readString(models.resolve(subtype)))
          .as(subtype)
          .doesNotContain("use super::*;");
    }
    assertThat(Files.readString(models.resolve("set_pet_avatar_thumbnail_request.rs")))
        .doesNotContain("use super::*;");
  }
}
