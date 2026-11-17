package io.github.mridang.codegen.generators.go;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;

class BetterGoCodegenTest {

  private static Map<String, Object> arg(String kind) {
    final Map<String, Object> m = new HashMap<>();
    m.put("isPath", "path".equals(kind));
    m.put("isOptions", "options".equals(kind));
    m.put("isServer", "server".equals(kind));
    m.put("paramName", kind);
    return m;
  }

  private static List<Object> names(List<Map<String, Object>> args) {
    final List<Object> out = new ArrayList<>();
    for (final Map<String, Object> a : args) {
      out.add(a.get("paramName"));
    }
    return out;
  }

  @Test
  void movesTheServerInFrontOfTheOptionsObject() {
    final List<Map<String, Object>> args = new ArrayList<>();
    args.add(arg("path"));
    args.add(arg("options"));
    args.add(arg("server"));
    BetterGoCodegen.moveServerBeforeOptions(args);
    assertThat(names(args)).containsExactly("path", "server", "options");
  }

  @Test
  void leavesAServerOnlySignatureUnchanged() {
    final List<Map<String, Object>> args = new ArrayList<>();
    args.add(arg("path"));
    args.add(arg("server"));
    BetterGoCodegen.moveServerBeforeOptions(args);
    assertThat(names(args)).containsExactly("path", "server");
  }

  /* F1154: an apiKey name goes into a Go string literal, so a quote, a backslash and a control
  character are escaped while a dollar sign and an interpolation opener stay literal. */
  @Test
  void escapesQuoteBackslashAndControlCharactersInAnApiKeyName() {
    assertThat(BetterGoCodegen.goStringBody("X-Api\"Key\\")).isEqualTo("X-Api\\\"Key\\\\");
    assertThat(BetterGoCodegen.goStringBody("X-$Key#{x}")).isEqualTo("X-$Key#{x}");
    assertThat(BetterGoCodegen.goStringBody("a\nb\u0001")).isEqualTo("a\\nb\\x01");
  }

  /* F308: an integer enum member is PascalCase like every other enum member. */
  @Test
  void anIntegerEnumMemberIsPascalCase() {
    assertThat(BetterGoCodegen.pascalFromUpperSnake("NUMBER_1")).isEqualTo("Number1");
    assertThat(BetterGoCodegen.pascalFromUpperSnake("NUMBER_MINUS_1")).isEqualTo("NumberMinus1");
    assertThat(BetterGoCodegen.pascalFromUpperSnake("NUMBER_1_DOT_5")).isEqualTo("Number1Dot5");
    assertThat(new BetterGoCodegen().toEnumVarName("-2", "int32")).isEqualTo("NumberMinus2");
  }

  /* F878: a oneOf variant is named from its Go type in the typed constructor and accessors. */
  @Test
  void aUnionVariantIsNamedFromItsGoType() {
    assertThat(BetterGoCodegen.variantName("DryFood")).isEqualTo("DryFood");
    assertThat(BetterGoCodegen.variantName("string")).isEqualTo("String");
    assertThat(BetterGoCodegen.variantName("[]byte")).isEqualTo("ByteSlice");
    assertThat(BetterGoCodegen.variantName("[][]byte")).isEqualTo("ByteSliceSlice");
    assertThat(BetterGoCodegen.variantName("map[string]int32")).isEqualTo("Int32Map");
    assertThat(BetterGoCodegen.variantName("uuid.UUID")).isEqualTo("UUID");
    assertThat(BetterGoCodegen.variantName("*Pet")).isEqualTo("Pet");
  }

  /* F877: a slice, a map, a string or a byte slice comes back by value, a model by pointer. */
  @Test
  void aSliceMapStringOrByteSliceIsReturnedByValue() {
    assertThat(BetterGoCodegen.returnsByValue("[]Pet")).isTrue();
    assertThat(BetterGoCodegen.returnsByValue("[]byte")).isTrue();
    assertThat(BetterGoCodegen.returnsByValue("map[string]int32")).isTrue();
    assertThat(BetterGoCodegen.returnsByValue("string")).isTrue();
    assertThat(BetterGoCodegen.returnsByValue("Pet")).isFalse();
    assertThat(BetterGoCodegen.returnsByValue("int32")).isFalse();
    assertThat(BetterGoCodegen.returnsByValue(null)).isFalse();
  }

  /* F1055: a path or body parameter named like something the operation body declares is
  renamed, so the generated method still compiles. */
  @Test
  void renamesAParameterNamedLikeAnInjectedArgumentOrALocal() {
    final BetterGoCodegen codegen = new BetterGoCodegen();
    assertThat(codegen.toParamName("options")).isEqualTo("optionsParam");
    assertThat(codegen.toParamName("ctx")).isEqualTo("ctxParam");
    assertThat(codegen.toParamName("path")).isEqualTo("pathParam");
    assertThat(codegen.toParamName("callOptions")).isEqualTo("callOptionsParam");
    assertThat(codegen.toParamName("name")).isEqualTo("name");
  }

  /* F827: per-call settings are the variadic RequestOption of every operation, so no Options
  struct is minted for an operation without parameters. */
  @Test
  void callSettingsRideTheVariadicTailNotAMintedOptionsStruct() {
    assertThat(new BetterGoCodegen().mintsOptionsForEveryOperation()).isFalse();
  }

  private static CodegenProperty refProperty(@Nullable String defaultValue, Object isEnum) {
    final CodegenProperty prop = new CodegenProperty();
    prop.defaultValue = defaultValue;
    prop.vendorExtensions.put("siblingDefaultIsEnum", isEnum);
    prop.vendorExtensions.put("siblingDefaultEnumType", "Availability");
    prop.vendorExtensions.put("siblingDefaultEnumVar", "OnHold");
    return prop;
  }

  /* F866: a default next to a $ref to an enum becomes the property's default, as the constant. */
  @Test
  void aSiblingEnumDefaultBecomesThePropertyDefault() {
    final CodegenProperty prop = refProperty(null, true);
    new BetterGoCodegen().fixEnumDefaultValue(prop, new CodegenModel());
    assertThat(prop.defaultValue).isEqualTo("AvailabilityOnHold");
  }

  @Test
  void aSiblingDefaultNeverReplacesTheSchemasOwnDefault() {
    final CodegenProperty prop = refProperty("\"x\"", true);
    new BetterGoCodegen().fixEnumDefaultValue(prop, new CodegenModel());
    assertThat(prop.defaultValue).isEqualTo("\"x\"");
  }

  @Test
  void aSiblingDefaultOnANonEnumReferenceLeavesTheDefaultUnset() {
    final CodegenProperty prop = refProperty(null, false);
    new BetterGoCodegen().fixEnumDefaultValue(prop, new CodegenModel());
    assertThat(prop.defaultValue).isNull();
  }
}
