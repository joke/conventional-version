package io.github.joke.conventionalversion.git;

import io.github.joke.conventionalversion.calc.ChangelogReader;
import io.github.joke.conventionalversion.calc.RepositoryState;
import io.github.joke.conventionalversion.calc.SemanticVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Gathers everything the calculation needs from a checkout.
 *
 * <p>The changelog says <em>which</em> version was last released, the tag says <em>where</em>. Tags
 * alone are not enough: release-please's notion of "released" is a GitHub Release it created, so a
 * tag made by hand is invisible to it and reading the highest tag would disagree with release-please
 * exactly when someone has tagged manually.
 */
public class RepositoryStateReader {

    private static final String CHANGELOG_FILE = "CHANGELOG.md";

    private final GitRepository repository;
    private final ChangelogReader changelogReader;
    private final Path projectDirectory;

    public RepositoryStateReader(
            final GitRepository repository, final ChangelogReader changelogReader, final Path projectDirectory) {
        this.repository = repository;
        this.changelogReader = changelogReader;
        this.projectDirectory = projectDirectory;
    }

    public RepositoryState read(final String tagPrefix) {
        repository.verifyUsable();
        final var recorded = findRecordedRelease();
        final var headSha = repository.headSha();
        return recorded.map(version -> stateAtRelease(version, tagPrefix, headSha))
                .orElseGet(() -> stateWithoutRelease(headSha));
    }

    @VisibleForTesting
    protected Optional<SemanticVersion> findRecordedRelease() {
        return changelogReader.findLatestRelease(readChangelog());
    }

    /** An absent changelog is the "never released" case, not an error. */
    @VisibleForTesting
    protected String readChangelog() {
        final var changelog = projectDirectory.resolve(CHANGELOG_FILE);
        if (!Files.isRegularFile(changelog)) {
            return "";
        }
        try {
            return Files.readString(changelog);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @VisibleForTesting
    protected RepositoryState stateAtRelease(
            final SemanticVersion recorded, final String tagPrefix, final String headSha) {
        final var tag = tagPrefix + recorded;
        final var baseSha = repository
                .findTaggedCommit(tag)
                .orElseThrow(() -> new ConventionalVersionException("The changelog records " + recorded
                        + " as the last release but no tag " + tag
                        + " exists. Fetch tags, or correct the tag prefix."));
        return new RepositoryState(recorded, baseSha.equals(headSha), repository.commitMessagesSince(baseSha), headSha);
    }

    @VisibleForTesting
    protected RepositoryState stateWithoutRelease(final String headSha) {
        return new RepositoryState(null, false, allCommitMessages(), headSha);
    }

    /** An empty repository has no HEAD to log, which is still the "never released" case. */
    @VisibleForTesting
    protected List<String> allCommitMessages() {
        return repository.allCommitMessages();
    }
}
