package io.github.mridang.codegen.generators;

/**
 * Opt-in interface for language generators that produce type-signature files
 * alongside their regular source output. The base class
 * ({@link AbstractBetterCodegen}) uses {@code instanceof} dispatch to activate
 * the type-file infrastructure for any implementing generator.
 *
 * <p>Ruby is currently the only implementor, producing {@code .rbs} files that
 * Steep uses for static type-checking. Any future language that needs companion
 * type files (e.g. Sorbet {@code .rbi}, TypeScript {@code .d.ts}) simply
 * implements this interface and overrides only the values that differ from the
 * defaults.
 *
 * <p>All methods have defaults tuned for Ruby/RBS; implementors that match
 * those defaults need not override anything.
 */
public interface WithTypeSignatureSupport {

    /**
     * File extension of the generated type-signature files, including the
     * leading dot. Default is {@code ".rbs"} (Ruby Signatures).
     */
    default String getSignatureFileExtension() {
        return ".rbs";
    }

    /**
     * Root directory (relative to the generator output folder) where
     * type-signature files should be collected after generation. Default is
     * {@code "sig"} as required by Ruby's Steep tooling.
     */
    default String getSignatureDir() {
        return "sig";
    }

    /**
     * Source directory prefix (relative to the generator output folder) that
     * generated signature files are initially placed under before being moved
     * to {@link #getSignatureDir()}. Default is {@code "lib"} (Ruby's source
     * root).
     */
    default String getSignatureSourceDir() {
        return "lib";
    }

    /**
     * Path (relative to the template root) of the Mustache template used to
     * render the type-signature companion file for each generated authenticator.
     * Return an empty string to skip companion file generation.
     * Default is {@code "auth/scheme_authenticator_rbs.mustache"}.
     */
    default String getAuthenticatorSignatureTemplate() {
        return "auth/scheme_authenticator_rbs.mustache";
    }
}
