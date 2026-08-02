package io.github.joke.conventionalversion.calc;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the last released version out of the changelog release-please maintains.
 *
 * <p>The changelog rather than the tags is the base, because release-please's notion of "released"
 * is a GitHub Release it created, not a git ref. A tag made by hand is invisible to it, and a
 * tag-reading plugin would disagree with release-please exactly when someone has tagged manually.
 */
public class ChangelogReader {

    /**
     * Both heading shapes release-please emits. The first release has no compare link
     * ({@code ## 1.0.0 (2022-02-12)}), later ones do
     * ({@code ## [1.3.0](…/compare/v1.2.0...v1.3.0) (2022-02-12)}), and a patch release is rendered
     * one level deeper with {@code ###}.
     */
    private static final Pattern HEADING =
            Pattern.compile("^#{2,3}+ ++\\[?+(\\d++\\.\\d++\\.\\d++)]?+", Pattern.MULTILINE);

    private static final int VERSION_GROUP = 1;

    /**
     * The topmost heading is the most recent release. Returns empty when the changelog is absent or
     * contains no parseable heading, which is the "never released" case rather than an error.
     */
    public Optional<SemanticVersion> findLatestRelease(final String changelog) {
        return HEADING.matcher(changelog)
                .results()
                .findFirst()
                .map(result -> result.group(VERSION_GROUP))
                .flatMap(SemanticVersion::parse);
    }
}
