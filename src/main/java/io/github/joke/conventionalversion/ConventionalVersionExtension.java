package io.github.joke.conventionalversion;

import org.gradle.api.provider.Property;

/**
 * Configuration, in the settings file.
 *
 * <p>Every option is named after its release-please counterpart and carries release-please's default,
 * so the two configurations can be compared by eye and a divergence is greppable.
 */
public interface ConventionalVersionExtension {

    /**
     * The version a project that has never released starts at. release-please {@code initial-version};
     * defaults to {@code 1.0.0}, which is what release-please actually cuts for a first release.
     */
    Property<String> getInitialVersion();

    /** Prefix on release tags. release-please {@code tag-prefix}; defaults to {@code v}. */
    Property<String> getTagPrefix();

    /**
     * Below {@code 1.0.0}, treat a breaking change as a minor bump instead of a major one.
     * release-please {@code bump-minor-pre-major}; defaults to {@code false}. No effect once the major
     * is non-zero.
     */
    Property<Boolean> getBumpMinorPreMajor();

    /**
     * Below {@code 1.0.0}, treat a feature as a patch bump instead of a minor one. release-please
     * {@code bump-patch-for-minor-pre-major}; defaults to {@code false}. No effect once the major is
     * non-zero.
     */
    Property<Boolean> getBumpPatchForMinorPreMajor();
}
