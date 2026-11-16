package io.github.mridang.codegen.generators.rust;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

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
  void aDefaultWrittenNextToARefToAnEnumNamesTheTypedMember() {
    final StringSchema availability = new StringSchema();
    availability.setEnum(List.of("Available", "on-hold"));
    codegen.setOpenAPI(
        new OpenAPI().components(new Components().addSchemas("Availability", availability)));
    final Schema<?> reference = new Schema<>();
    reference.set$ref("#/components/schemas/Availability");
    reference.setDefault("on-hold");

    assertThat(codegen.toDefaultValue(reference)).isEqualTo("Availability::OnHold");
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
  void renamesAParameterNamedLikeAnInjectedArgumentOrALocal() {
    assertThat(codegen.toParamName("options")).isEqualTo("options_param");
    assertThat(codegen.toParamName("server")).isEqualTo("server_param");
    assertThat(codegen.toParamName("path")).isEqualTo("path_param");
    assertThat(codegen.toParamName("headers")).isEqualTo("headers_param");
    assertThat(codegen.toParamName("item_id")).isEqualTo("item_id");
  }
}
