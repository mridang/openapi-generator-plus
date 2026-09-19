package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the title suffix that templates append for themselves.
 *
 * <p>Fifteen templates write {@code {{appNameBase}} SDK} or a longer variant
 * of it. That reads correctly only while the title does not already end that
 * way, and Zitadel's title is "Zitadel SDK", so every generated document
 * opened with "Zitadel SDK SDK". The fixture spec is titled "Swagger
 * Petstore", so the suite could not show it — the same blind spot that has hidden
 * several defects here, where a value is only correct for the one spec the
 * generator is developed against.
 */
class AppNameBaseTest {

    @Test
    @DisplayName("a title already ending in SDK does not keep its suffix")
    void stripsTrailingSdk() {
        assertEquals("Zitadel", AbstractBetterCodegen.appNameBase("Zitadel SDK"));
    }

    @Test
    @DisplayName("the suffix is matched whatever its case")
    void stripsTrailingSdkRegardlessOfCase() {
        assertEquals("Zitadel", AbstractBetterCodegen.appNameBase("Zitadel sdk"));
        assertEquals("Zitadel", AbstractBetterCodegen.appNameBase("Zitadel Sdk"));
    }

    @Test
    @DisplayName("an ordinary title is left exactly as it is")
    void leavesOrdinaryTitleAlone() {
        assertEquals("Swagger Petstore", AbstractBetterCodegen.appNameBase("Swagger Petstore"));
    }

    /* The suffix is only a suffix when it stands as its own word. A title
       whose last word merely ends in those three letters is not saying
       "SDK", and truncating it would corrupt a real name. */
    @Test
    @DisplayName("a word that merely ends in those letters is not a suffix")
    void doesNotTruncateWordEndingInSdk() {
        assertEquals("MySDK", AbstractBetterCodegen.appNameBase("MySDK"));
        assertEquals("Acme WebSDK", AbstractBetterCodegen.appNameBase("Acme WebSDK"));
    }

    @Test
    @DisplayName("surrounding and internal whitespace is tolerated")
    void toleratesWhitespace() {
        assertEquals("Zitadel", AbstractBetterCodegen.appNameBase("  Zitadel   SDK  "));
    }

    /* Stripping would leave nothing to name the package after, so the
       original is kept: "SDK SDK" is poor, but an empty heading is worse. */
    @Test
    @DisplayName("a title that is nothing but the suffix is kept")
    void keepsTitleThatIsOnlyTheSuffix() {
        assertEquals("SDK", AbstractBetterCodegen.appNameBase("SDK"));
    }

    @Test
    @DisplayName("an absent title yields an empty name rather than null")
    void handlesMissingTitle() {
        assertEquals("", AbstractBetterCodegen.appNameBase(null));
    }
}
