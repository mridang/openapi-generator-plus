package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;

import java.util.Map;

class BetterNodeCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("node-plus",
            newTempFolder(),
            Map.of(
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api"
            ));
    }
}
