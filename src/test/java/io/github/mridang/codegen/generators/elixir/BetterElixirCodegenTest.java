package io.github.mridang.codegen.generators.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenParameter;

class BetterElixirCodegenTest {

  @Test
  void quotesAndEscapesAnApiKeyNameSoItCannotBreakTheLiteral() {
    assertThat(BetterElixirCodegen.toElixirStringLiteral("X-Api-Key")).isEqualTo("\"X-Api-Key\"");
    assertThat(BetterElixirCodegen.toElixirStringLiteral("X-Api\"Key\\"))
        .isEqualTo("\"X-Api\\\"Key\\\\\"");
    assertThat(BetterElixirCodegen.toElixirStringLiteral("a\nb\tc"))
        .isEqualTo("\"a\\nb\\tc\"");
  }

  @Test
  void escapesTheInterpolationOpenerAndKeepsADollarLiteral() {
    assertThat(BetterElixirCodegen.toElixirStringLiteral("#{x}")).isEqualTo("\"\\#{x}\"");
    assertThat(BetterElixirCodegen.toElixirStringLiteral("$apiKey")).isEqualTo("\"$apiKey\"");
  }

  @Test
  void leavesAUrlUntouchedAndTurnsAMissingValueIntoAnEmptyLiteral() {
    assertThat(BetterElixirCodegen.toElixirStringLiteral("https://auth.example.com/token?a=b&c=d"))
        .isEqualTo("\"https://auth.example.com/token?a=b&c=d\"");
    assertThat(BetterElixirCodegen.toElixirStringLiteral(null)).isEqualTo("\"\"");
  }

  @Test
  void neutralisesTheInterpolationOpenerInProse() {
    assertThat(new BetterElixirCodegen().escapeUnsafeCharacters("a #{b} c"))
        .isEqualTo("a \\#{b} c");
    assertThat(BetterElixirCodegen.toHeredocText("a\\b #{c}")).isEqualTo("a\\\\b \\#{c}");
    assertThat(BetterElixirCodegen.toHeredocText(null)).isEmpty();
  }

  @Test
  void percentEncodesAnEnumWireValueAndPrefixesANumber() {
    assertThat(BetterElixirCodegen.toEnumWireToken("\"o'clock\"")).isEqualTo("o%27clock");
    assertThat(BetterElixirCodegen.toEnumWireToken("\"a\\\\b\"")).isEqualTo("a%5Cb");
    assertThat(BetterElixirCodegen.toEnumWireToken("\"In-Stock\"")).isEqualTo("In-Stock");
    assertThat(BetterElixirCodegen.toEnumWireToken("3")).isEqualTo("#3");
  }

  @Test
  void qualifiesABareModelAliasToItsStructTypespec() {
    assertThat(BetterElixirCodegen.toModuleTypespec("Tag", "Client"))
        .isEqualTo("Client.Models.Tag.t()");
    assertThat(BetterElixirCodegen.toModuleTypespec("[Tag]", "Client"))
        .isEqualTo("[Client.Models.Tag.t()]");
    assertThat(BetterElixirCodegen.toModuleTypespec("integer()", "Client")).isEqualTo("integer()");
    assertThat(BetterElixirCodegen.toModuleTypespec(null, "Client")).isEmpty();
  }

  @Test
  void loadsTheReservedWordListAndMapsTheStdlibTypes() {
    final BetterElixirCodegen codegen = new BetterElixirCodegen();
    assertThat(codegen.reservedWords()).contains("def", "end", "nil", "true", "false");
    assertThat(codegen.typeMapping()).containsEntry("string", "String.t()")
        .containsEntry("time", "Time.t()").containsEntry("duration", "Duration.t()");
  }

  @Test
  void suffixesAParameterNamedLikeABindingTheOperationMakesItself() {
    final BetterElixirCodegen codegen = new BetterElixirCodegen();
    for (final String bound : new String[] {"api", "opts", "options", "auth", "path", "server"}) {
      assertThat(codegen.toParamName(bound)).isEqualTo(bound + "_param");
    }
    assertThat(codegen.toParamName("petId")).isEqualTo("pet_id");
  }

  @Test
  void carriesTheNamedExamplesOfAnOptionalParameterIntoItsOptionsDocs() {
    final CodegenParameter parameter = new CodegenParameter();
    parameter.vendorExtensions = new HashMap<>();
    final Map<String, Object> example = new HashMap<>();
    example.put("summary", "Only #{open} ones");
    example.put("value", "open");
    parameter.vendorExtensions.put("pluralExamples", List.of(example));

    assertThat(BetterElixirCodegen.toExampleDocs(parameter))
        .containsExactly(Map.of("summary", "Only \\#{open} ones", "value", "open"));
    assertThat(BetterElixirCodegen.toExampleDocs(new CodegenParameter())).isEmpty();
  }
}
