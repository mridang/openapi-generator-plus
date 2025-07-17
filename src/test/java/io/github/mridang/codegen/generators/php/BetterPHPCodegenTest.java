package io.github.mridang.codegen.generators.php;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;

import java.util.Map;

class BetterPHPCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("php-plus",
            newTempFolder(),
            Map.of(
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api"
            ));
    }
}
