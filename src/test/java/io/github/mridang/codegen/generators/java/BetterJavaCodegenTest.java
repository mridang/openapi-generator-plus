package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;

import java.nio.file.Paths;
import java.util.Map;

class BetterJavaCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("java-plus",
            Paths.get("src/test/java/io/github/mridang/codegen/generators/java/.out"),
            Map.of(
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api")
        );
    }
}
