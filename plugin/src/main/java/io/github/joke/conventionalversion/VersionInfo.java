package io.github.joke.conventionalversion;

import io.github.joke.conventionalversion.calc.Bump;
import org.gradle.api.provider.Property;

/**
 * What the calculation concluded, readable from any project's build logic.
 *
 * <p>Registered on every project by the settings plugin, so build logic never reaches across project
 * boundaries to obtain it.
 */
public interface VersionInfo {

    /** The same string assigned as {@code project.version}. */
    Property<String> getVersion();

    /** What the analysed commits implied, before any override or floor was applied. */
    Property<Bump> getBumpType();

    /**
     * Whether the range warrants a release. Gate publishing on this rather than inferring intent from
     * the version string: a range with no releasable commits still gets a {@code -SNAPSHOT} version,
     * because Gradle requires one.
     */
    Property<Boolean> getReleasable();

    /**
     * The commit the build was produced from. Belongs in the jar manifest, not in the coordinate - a
     * version that changes every commit is not a usable snapshot.
     */
    Property<String> getSha();
}
