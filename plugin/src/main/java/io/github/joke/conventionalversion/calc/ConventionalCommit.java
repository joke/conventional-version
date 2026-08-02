package io.github.joke.conventionalversion.calc;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A single commit reduced to the facts that affect the version.
 *
 * <p>A message that does not conform to the Conventional Commits specification is represented with a
 * {@code null} type rather than by an exception, because a repository is free to contain any commit
 * message and an unparseable one simply contributes nothing.
 *
 * @param type the commit type, or {@code null} when the header does not conform
 * @param scope the optional scope; captured but not yet used to derive per-module versions
 * @param breaking whether the commit declares a breaking change
 * @param releaseAs the version named by a {@code Release-As:} footer, or {@code null}
 */
public record ConventionalCommit(
        @Nullable String type,
        @Nullable String scope,
        boolean breaking,
        @Nullable SemanticVersion releaseAs) {

    /** A message that conforms to nothing and names no version. */
    public static ConventionalCommit nonConforming() {
        return new ConventionalCommit(null, null, false, null);
    }

    public Optional<String> findType() {
        return Optional.ofNullable(type);
    }

    public Optional<SemanticVersion> findReleaseAs() {
        return Optional.ofNullable(releaseAs);
    }
}
