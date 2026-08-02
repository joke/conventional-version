package io.github.joke.conventionalversion.calc;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Extracts the version-relevant facts from a raw commit message.
 *
 * <p>Only what the calculation needs is parsed: a description, a footer map and commit references
 * would all be dead weight in a component that never renders a changelog.
 */
public class CommitMessageParser {

    private static final Pattern HEADER =
            Pattern.compile("(?<type>[a-zA-Z][\\w-]*+)" + "(?:\\((?<scope>[^()]++)\\))?" + "(?<breaking>!)?" + ": .++");

    /**
     * Both spellings the Conventional Commits specification permits. Uppercase only, as the
     * specification requires, and anchored to a line start so a mention inside a description does not
     * count.
     */
    private static final Pattern BREAKING_FOOTER = Pattern.compile("^BREAKING[ -]CHANGE: ", Pattern.MULTILINE);

    /**
     * Case-insensitive, matching release-please, and tolerant of a {@code v} prefix. The version is
     * captured by index rather than by name because {@code MatchResult.group(String)} only exists
     * from Java 20 and this artifact targets 17.
     *
     * <p>The trailing whitespace class is horizontal only. A possessive {@code \s*+} would consume
     * the line terminator and then be unable to backtrack for {@code $}, so a footer with any line
     * after it - a {@code Signed-off-by:} trailer, a second override - would silently fail to match.
     */
    private static final Pattern RELEASE_AS = Pattern.compile(
            "^release-as:[ \\t]*+v?(\\d++\\.\\d++\\.\\d++)[ \\t]*+$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final int RELEASE_AS_VERSION_GROUP = 1;

    public ConventionalCommit parse(final String message) {
        final var releaseAs = findReleaseAs(message).orElse(null);
        final var matcher = HEADER.matcher(headerOf(message));
        if (!matcher.matches()) {
            return new ConventionalCommit(null, null, false, releaseAs);
        }
        return new ConventionalCommit(
                matcher.group("type"),
                matcher.group("scope"),
                isBreaking(matcher.group("breaking"), message),
                releaseAs);
    }

    @VisibleForTesting
    protected String headerOf(final String message) {
        return message.lines().findFirst().orElse("");
    }

    /**
     * Whether the commit declares a breaking change, by either spelling the specification allows.
     *
     * @param breakingMarker the {@code !} captured from the header, or {@code null} when absent
     */
    @VisibleForTesting
    protected boolean isBreaking(final @Nullable String breakingMarker, final String message) {
        return breakingMarker != null || BREAKING_FOOTER.matcher(message).find();
    }

    /**
     * The last footer in the message wins, matching release-please's handling of repeated overrides.
     */
    @VisibleForTesting
    protected Optional<SemanticVersion> findReleaseAs(final String message) {
        return RELEASE_AS
                .matcher(message)
                .results()
                .map(result -> result.group(RELEASE_AS_VERSION_GROUP))
                .reduce((earlier, later) -> later)
                .flatMap(SemanticVersion::parse);
    }
}
