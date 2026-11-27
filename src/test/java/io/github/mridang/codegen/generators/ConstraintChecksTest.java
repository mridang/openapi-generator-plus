package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenProperty;

class ConstraintChecksTest {

  private static List<String> messages(List<Map<String, Object>> checks) {
    return checks.stream().map(c -> String.valueOf(c.get("message"))).collect(Collectors.toList());
  }

  @Test
  @DisplayName("a plain string gets its length and pattern rules in a fixed order")
  void stringKeywords() {
    final CodegenProperty prop = new CodegenProperty();
    prop.isString = true;
    final StringSchema schema = new StringSchema();
    schema.setPattern("^[a-z]+$");
    schema.setMaxLength(5);
    schema.setMinLength(2);
    final List<Map<String, Object>> checks = ConstraintChecks.checksFor(prop, schema);
    assertEquals(
        List.of(
            "must be at least 2 characters long",
            "must be at most 5 characters long",
            "must match the pattern ^[a-z]+$"),
        messages(checks));
    assertEquals("^[a-z]+$", checks.get(2).get("pattern"));
    assertEquals(true, checks.get(0).get("isMinLength"));
  }

  @Test
  @DisplayName("the OAS 3.1 numeric exclusive bounds and the 3.0 boolean ones read the same")
  void numericBounds() {
    final CodegenProperty prop = new CodegenProperty();
    prop.isNumber = true;
    final NumberSchema v31 = new NumberSchema();
    v31.setExclusiveMinimumValue(BigDecimal.ZERO);
    v31.setExclusiveMaximumValue(new BigDecimal("1.50"));
    v31.setMultipleOf(new BigDecimal("0.5"));
    assertEquals(
        List.of("must be greater than 0", "must be less than 1.5", "must be a multiple of 0.5"),
        messages(ConstraintChecks.checksFor(prop, v31)));
    final NumberSchema v30 = new NumberSchema();
    v30.setMinimum(BigDecimal.ZERO);
    v30.setExclusiveMinimum(true);
    v30.setMaximum(BigDecimal.TEN);
    assertEquals(
        List.of("must be greater than 0", "must be less than or equal to 10"),
        messages(ConstraintChecks.checksFor(prop, v30)));
  }

  @Test
  @DisplayName("an integer limit carries a double literal for languages that compare as doubles")
  void integerLimitLiteral() {
    final CodegenProperty prop = new CodegenProperty();
    prop.isInteger = true;
    final IntegerSchema schema = new IntegerSchema();
    schema.setMinimum(BigDecimal.ONE);
    final Map<String, Object> check = ConstraintChecks.checksFor(prop, schema).get(0);
    assertEquals("1", check.get("limit"));
    assertEquals("1.0", check.get("limitDouble"));
    assertEquals(true, check.get("limitIsInteger"));
  }

  @Test
  @DisplayName("a keyword is checked only on the kind of value it applies to")
  void keywordsFollowTheValueKind() {
    final CodegenProperty uuid = new CodegenProperty();
    uuid.isString = true;
    uuid.isUuid = true;
    final StringSchema schema = new StringSchema();
    schema.setMinLength(1);
    assertTrue(ConstraintChecks.checksFor(uuid, schema).isEmpty());
    final CodegenProperty list = new CodegenProperty();
    list.isArray = true;
    list.isContainer = true;
    final ArraySchema array = new ArraySchema();
    array.setMinItems(1);
    array.setMaxItems(3);
    array.setMinLength(4);
    assertEquals(
        List.of("must have at least 1 items", "must have at most 3 items"),
        messages(ConstraintChecks.checksFor(list, array)));
  }

  @Test
  @DisplayName("a limit is written as plain decimal text without trailing zeros")
  void plainLimits() {
    assertEquals("100", ConstraintChecks.plain(new BigDecimal("1E+2")));
    assertEquals("0", ConstraintChecks.plain(new BigDecimal("0.000")));
    assertEquals("2.5", ConstraintChecks.plain(new BigDecimal("2.50")));
  }
}
