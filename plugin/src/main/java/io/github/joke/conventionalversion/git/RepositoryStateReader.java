package io.github.joke.conventionalversion.git;

import io.github.joke.conventionalversion.calc.Commit;
import io.github.joke.conventionalversion.calc.RepositoryState;
import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.config.ReleaseConfiguration;
import io.github.joke.conventionalversion.config.ReleasePackage;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Gathers what the calculation needs, once per build, for every package at once.
 *
 * <p>The manifest says <em>which</em> version each package last released, the tag says <em>where</em>.
 * The whole first-parent history is read a single time and each package's range is sliced from it at
 * that package's own base commit, so the number of git processes does not grow with the number of
 * packages.
 */
public class RepositoryStateReader {

    private final GitRepository repository;

    public RepositoryStateReader(final GitRepository repository) {
        this.repository = repository;
    }

    /**
     * The state of every declared package, paired with the package itself.
     *
     * <p>Paired rather than keyed by path so that callers never look a package up and never have to
     * handle an absent one: the pairing is the invariant, expressed in the type.
     */
    public List<PackageState> read(
            final ReleaseConfiguration configuration, final Map<String, SemanticVersion> released) {
        repository.verifyUsable();
        final var headSha = repository.headSha();
        final var history = repository.allCommits();
        return configuration.packages().stream()
                .map(declared ->
                        new PackageState(declared, stateOf(declared, released.get(declared.path()), history, headSha)))
                .toList();
    }

    /** One package and the repository facts its own range yielded. */
    public record PackageState(ReleasePackage declared, RepositoryState state) {}

    @VisibleForTesting
    protected RepositoryState stateOf(
            final ReleasePackage declared,
            final @Nullable SemanticVersion recorded,
            final List<Commit> history,
            final String headSha) {
        if (recorded == null) {
            return new RepositoryState(null, false, messagesOf(declared, history), headSha);
        }
        final var baseSha = baseShaOf(declared, recorded);
        return new RepositoryState(
                recorded, baseSha.equals(headSha), messagesOf(declared, since(baseSha, history)), headSha);
    }

    /** The commit a package's recorded release was cut at, found by the tag release-please would create. */
    @VisibleForTesting
    protected String baseShaOf(final ReleasePackage declared, final SemanticVersion recorded) {
        final var tag = declared.tag(recorded);
        return repository
                .findTaggedCommit(tag)
                .orElseThrow(() -> new ConventionalVersionException("The release manifest records " + recorded
                        + " for the package '" + declared.path() + "' but no tag " + tag
                        + " exists. Fetch tags, or correct the package's component and tag format."));
    }

    /** The history after a package's base commit. A base that is not on the first-parent line yields nothing. */
    @VisibleForTesting
    protected List<Commit> since(final String baseSha, final List<Commit> history) {
        final var shas = history.stream().map(Commit::sha).toList();
        final var base = shas.indexOf(baseSha);
        return base < 0 ? List.of() : history.subList(base + 1, history.size());
    }

    /** Only the commits that touched a path this package claims contribute to its bump. */
    @VisibleForTesting
    protected List<String> messagesOf(final ReleasePackage declared, final List<Commit> commits) {
        return commits.stream()
                .filter(commit -> touches(declared, commit))
                .map(Commit::message)
                .toList();
    }

    @VisibleForTesting
    protected boolean touches(final ReleasePackage declared, final Commit commit) {
        return commit.paths().stream().anyMatch(declared::claims);
    }

    /** Where release-please keeps its configuration, which need not be the Gradle build's root. */
    public String repositoryRoot() {
        repository.verifyUsable();
        return repository.repositoryRoot();
    }
}
