package io.github.mridang.codegen.generators.php;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class BetterPHPCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("php-plus", Paths.get("src/test/java/io/github/mridang/codegen/generators/php/.out"));
    }
}
