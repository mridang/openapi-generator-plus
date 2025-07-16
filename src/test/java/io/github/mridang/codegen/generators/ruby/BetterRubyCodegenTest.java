package io.github.mridang.codegen.generators.ruby;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;

import java.nio.file.Paths;
import java.util.Map;

class BetterRubyCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("ruby-plus",
            Paths.get("src/test/java/io/github/mridang/codegen/generators/ruby/.out"),
            Map.of(
                CodegenConstants.MODULE_NAME, "Testing::Client",
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api"
            )
        );
    }
}
