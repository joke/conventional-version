package io.github.joke.conventionalversion.calc;

/**
 * The inputs that change the calculated number, named after their release-please counterparts so a
 * divergence between the two configurations is greppable.
 *
 * @param initialVersion release-please {@code initial-version}
 * @param bumpMinorPreMajor release-please {@code bump-minor-pre-major}
 * @param bumpPatchForMinorPreMajor release-please {@code bump-patch-for-minor-pre-major}
 */
public record VersionPolicy(
        SemanticVersion initialVersion, boolean bumpMinorPreMajor, boolean bumpPatchForMinorPreMajor) {

    private static final SemanticVersion DEFAULT_INITIAL_VERSION = new SemanticVersion(1, 0, 0);

    /**
     * release-please's defaults. The initial version is {@code 1.0.0} rather than {@code 0.1.0}
     * because that is what release-please actually cuts for a first release.
     */
    public static VersionPolicy defaults() {
        return new VersionPolicy(DEFAULT_INITIAL_VERSION, false, false);
    }
}
