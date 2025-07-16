package io.github.mridang.codegen.generators.python;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class BetterPythonCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("python-plus", Paths.get("src/test/java/io/github/mridang/codegen/generators/python/.out"));
    }
}
