package io.github.mridang.codegen.generators.dart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetterDartCodegenTest {

  @Test
  void escapesQuoteDollarAndBackslashInAnApiKeyName() {
    assertThat(BetterDartCodegen.escapeDartString("X-Api'Key$ident\\x"))
        .isEqualTo("X-Api\\'Key\\$ident\\\\x");
  }

  @Test
  void leavesAPlainApiKeyNameUnchanged() {
    assertThat(BetterDartCodegen.escapeDartString("X-API-Key")).isEqualTo("X-API-Key");
  }

  @Test
  void placesTheServerBeforeTheOptionsObject() {
    final Map<String, Object> path = Map.of("paramName", "petId");
    final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);
    final Map<String, Object> server = Map.of("paramName", "server", "isServer", true);

    assertThat(BetterDartCodegen.serverBeforeOptions(List.of(path, options, server)))
        .containsExactly(path, server, options);
  }

  @Test
  void keepsASignatureWithoutAServerUnchanged() {
    final Map<String, Object> path = Map.of("paramName", "petId");
    final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);

    assertThat(BetterDartCodegen.serverBeforeOptions(List.of(path, options)))
        .containsExactly(path, options);
  }

  @Test
  void modelsNamedLikeACoreTypeGetThePrefix() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.toModelName("Error")).isEqualTo("ModelError");
    assertThat(codegen.toModelName("Exception")).isEqualTo("ModelException");
    assertThat(codegen.toModelName("Uri")).isEqualTo("ModelUri");
    assertThat(codegen.toModelName("Category")).isEqualTo("Category");
    assertThat(codegen.toModelName("Result")).isEqualTo("Result");
  }

  @Test
  void parametersNamedLikeInjectedArgumentsGetASuffix() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.toParamName("options")).isEqualTo("optionsParam");
    assertThat(codegen.toParamName("server")).isEqualTo("serverParam");
    assertThat(codegen.toParamName("path")).isEqualTo("pathParam");
    assertThat(codegen.toParamName("petId")).isEqualTo("petId");
  }
}
