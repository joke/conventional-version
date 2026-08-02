package io.github.joke.conventionalversion.calc;

import java.io.Serializable;

/**
 * The outcome of a calculation.
 *
 * <p>{@link Serializable} because this is the value a {@code ValueSource} hands back: it is stored in
 * the configuration cache entry and captured by the isolated action that assigns project versions.
 *
 * @param version the version string, bare on a release commit and {@code -SNAPSHOT} otherwise
 * @param bump what the analysed commits implied, before any override or floor was applied
 * @param releasable whether the range warrants a release; publishing is gated on this rather than on
 *     inferring intent from {@code version}
 * @param sha the commit the build was produced from, for the jar manifest rather than the coordinate
 */
public record VersionResult(String version, Bump bump, boolean releasable, String sha) implements Serializable {}
