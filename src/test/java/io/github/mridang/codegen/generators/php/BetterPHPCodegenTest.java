package io.github.mridang.codegen.generators.php;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;

import java.nio.file.Paths;
import java.util.Map;

class BetterPHPCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("php-plus",
            Paths.get("src/test/java/io/github/mridang/codegen/generators/php/.out"),
            Map.of(
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api"
            ));
    }
}
