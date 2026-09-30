package io.github.mridang.codegen.generators.php;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.openapitools.codegen.CodegenProperty;
import org.junit.jupiter.api.Test;

/**
 * Validates that {@link BetterPHPCodegen#phpDocReturnType(String, CodegenProperty)}
 * builds the {@code @return} / {@code @var} PHPDoc by descending the full return
 * item tree, so a nested container documents its precise inner value type rather
 * than collapsing it to {@code mixed}. The single-level build that preceded this
 * only read {@code items.dataType}, which for a {@code list<list<int>>} is the
 * inner {@code \Ds\Vector} container placeholder, and {@link
 * BetterPHPCodegen#withDsGenerics(String)} then widened the dangling generic to
 * {@code <mixed>} — losing the {@code int} element type in the docblock only
 * (the runtime deserialize descriptor was already precise).
 *
 * <p>This exercises the helper directly with hand-built {@link CodegenProperty}
 * trees because the shared spec fixture has no nested-container return shape, so
 * the golden-generation roundtrip cannot otherwise reach the regressed branch.
 */
class BetterPHPCodegenPhpDocReturnTypeTest {

    /* A leaf scalar: array element with no further nesting. */
    private static CodegenProperty leaf(String dataType) {
        CodegenProperty p = new CodegenProperty();
        p.dataType = dataType;
        return p;
    }

    private static CodegenProperty array(String dataType, CodegenProperty items) {
        CodegenProperty p = new CodegenProperty();
        p.dataType = dataType;
        p.isArray = true;
        p.items = items;
        return p;
    }

    private static CodegenProperty map(String dataType, CodegenProperty items) {
        CodegenProperty p = new CodegenProperty();
        p.dataType = dataType;
        p.isMap = true;
        p.items = items;
        return p;
    }

    /* A bare scalar return preserves the resolved outer type unchanged. */
    @Test
    void scalarReturnUsesOuterType() {
        assertEquals("string", BetterPHPCodegen.phpDocReturnType("string", leaf("string")));
    }

    /* A null property (void / unresolved) still normalises the outer type. */
    @Test
    void nullPropertyFallsBackToOuterType() {
        assertEquals("\\Ds\\Vector<mixed>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Vector", null));
    }

    @Test
    void nullOuterTypeYieldsNull() {
        assertNull(BetterPHPCodegen.phpDocReturnType(null, leaf("string")));
    }

    /* Single-level array: parity with the previous one-level build. */
    @Test
    void singleLevelArrayDocumentsElementType() {
        CodegenProperty rp = array("\\Ds\\Vector", leaf("int"));
        assertEquals("\\Ds\\Vector<int>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Vector", rp));
    }

    /* Single-level map: parity with the previous one-level build. */
    @Test
    void singleLevelMapDocumentsValueType() {
        CodegenProperty rp = map("\\Ds\\Map", leaf("string"));
        assertEquals("\\Ds\\Map<string, string>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Map", rp));
    }

    /*
     * The regression: a list<list<int>> must descend BOTH levels and keep the
     * int element, not collapse the inner \Ds\Vector to <mixed>.
     */
    @Test
    void nestedArrayDocumentsFullElementTree() {
        CodegenProperty rp = array("\\Ds\\Vector", array("\\Ds\\Vector", leaf("int")));
        assertEquals("\\Ds\\Vector<\\Ds\\Vector<int>>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Vector", rp));
    }

    /* A list of maps of int keeps the inner value type too. */
    @Test
    void arrayOfMapDocumentsValueType() {
        CodegenProperty rp = array("\\Ds\\Vector", map("\\Ds\\Map", leaf("int")));
        assertEquals("\\Ds\\Vector<\\Ds\\Map<string, int>>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Vector", rp));
    }

    /* A map of lists of model keeps the inner element type. */
    @Test
    void mapOfArrayDocumentsElementType() {
        CodegenProperty rp = map("\\Ds\\Map", array("\\Ds\\Vector", leaf("\\PetstoreClient\\Model\\Pet")));
        assertEquals("\\Ds\\Map<string, \\Ds\\Vector<\\PetstoreClient\\Model\\Pet>>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Map", rp));
    }

    /*
     * A genuinely element-less container (items missing) still gets default
     * generics from withDsGenerics so the docblock stays PHPStan-valid.
     */
    @Test
    void bareContainerLeafStillGetsDefaultGenerics() {
        CodegenProperty rp = array("\\Ds\\Vector", leaf("\\Ds\\Set"));
        assertEquals("\\Ds\\Vector<\\Ds\\Set<mixed>>",
                BetterPHPCodegen.phpDocReturnType("\\Ds\\Vector", rp));
    }
}
