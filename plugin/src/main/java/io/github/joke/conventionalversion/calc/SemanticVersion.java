package io.github.joke.conventionalversion.calc;

import static java.lang.Integer.parseInt;

import java.io.Serializable;
import java.util.Optional;
import java.util.regex.Pattern;

/** A {@code major.minor.patch} version, without pre-release or build metadata. */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion>, Serializable {

    private static final Pattern PATTERN = Pattern.compile("(\\d++)\\.(\\d++)\\.(\\d++)");

    public SemanticVersion {
        requireNonNegative(major, "major");
        requireNonNegative(minor, "minor");
        requireNonNegative(patch, "patch");
    }

    /** Parses {@code 1.2.3}, returning empty when the text is not exactly three numeric parts. */
    public static Optional<SemanticVersion> parse(final String text) {
        final var matcher = PATTERN.matcher(text.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new SemanticVersion(
                parseInt(matcher.group(1)), parseInt(matcher.group(2)), parseInt(matcher.group(3))));
    }

    private static void requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, but was " + value);
        }
    }

    /**
     * Whether this version is below {@code 1.0.0}, which is the condition both pre-major policies are
     * gated on.
     */
    public boolean isPreMajor() {
        return major == 0;
    }

    public SemanticVersion bumpMajor() {
        return new SemanticVersion(major + 1, 0, 0);
    }

    public SemanticVersion bumpMinor() {
        return new SemanticVersion(major, minor + 1, 0);
    }

    public SemanticVersion bumpPatch() {
        return new SemanticVersion(major, minor, patch + 1);
    }

    @Override
    public int compareTo(final SemanticVersion other) {
        final var byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        final var byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
