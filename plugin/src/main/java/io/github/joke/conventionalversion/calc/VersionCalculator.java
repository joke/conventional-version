package io.github.joke.conventionalversion.calc;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Turns gathered repository facts into a version, predicting what release-please will publish.
 *
 * <p>Pure: given the same {@link RepositoryState} and {@link VersionPolicy} it always returns the
 * same {@link VersionResult}, with no clock, no filesystem and no process involved.
 */
public class VersionCalculator {

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    private final CommitMessageParser parser;
    private final BumpReducer reducer;

    public VersionCalculator(final CommitMessageParser parser, final BumpReducer reducer) {
        this.parser = parser;
        this.reducer = reducer;
    }

    public VersionResult calculate(final RepositoryState state, final VersionPolicy policy) {
        return findReleasedVersion(state)
                .map(released -> releasedResult(released, state.headSha()))
                .orElseGet(() -> snapshotResult(state, policy));
    }

    /**
     * On a release commit the recorded version is the truth and nothing is derived - not the bump,
     * and not a {@code Release-As:} override earlier in history.
     */
    @VisibleForTesting
    protected Optional<SemanticVersion> findReleasedVersion(final RepositoryState state) {
        if (!state.headIsReleaseCommit()) {
            return Optional.empty();
        }
        return state.findRecordedRelease();
    }

    @VisibleForTesting
    protected VersionResult releasedResult(final SemanticVersion released, final String sha) {
        return new VersionResult(released.toString(), Bump.NONE, false, sha);
    }

    @VisibleForTesting
    protected VersionResult snapshotResult(final RepositoryState state, final VersionPolicy policy) {
        final var commits = parseAll(state.commitMessages());
        final var override = findOverride(commits);
        final var bump = reducer.reduce(commits);
        final var next = nextVersion(state.recordedRelease(), policy, override, bump);
        return new VersionResult(
                next + SNAPSHOT_SUFFIX, bump, bump.isReleasable() || override.isPresent(), state.headSha());
    }

    @VisibleForTesting
    protected List<ConventionalCommit> parseAll(final List<String> messages) {
        return messages.stream().map(parser::parse).toList();
    }

    /** The most recent override wins, which with an oldest-first range is the last one. */
    @VisibleForTesting
    protected Optional<SemanticVersion> findOverride(final List<ConventionalCommit> commits) {
        return commits.stream()
                .map(ConventionalCommit::findReleaseAs)
                .flatMap(Optional::stream)
                .reduce((earlier, later) -> later);
    }

    @VisibleForTesting
    protected SemanticVersion nextVersion(
            final @Nullable SemanticVersion recorded,
            final VersionPolicy policy,
            final Optional<SemanticVersion> override,
            final Bump bump) {
        if (override.isPresent()) {
            return override.get();
        }
        if (recorded == null) {
            return policy.initialVersion();
        }
        return applyBump(bump, recorded, policy);
    }

    @VisibleForTesting
    protected SemanticVersion applyBump(final Bump bump, final SemanticVersion base, final VersionPolicy policy) {
        return switch (effectiveBump(bump, base, policy)) {
            case MAJOR -> base.bumpMajor();
            case MINOR -> base.bumpMinor();
            // PATCH and NONE both land here. NONE floors at patch: Gradle needs a version even
            // when nothing is releasable, and reusing the base would shadow an immutably
            // published release. An explicit default rather than listing both, because javac
            // synthesises an unreachable throw for an exhaustive enum switch and no test can
            // ever kill a mutation of it.
            default -> base.bumpPatch();
        };
    }

    @VisibleForTesting
    protected Bump effectiveBump(final Bump bump, final SemanticVersion base, final VersionPolicy policy) {
        if (!base.isPreMajor()) {
            return bump;
        }
        return preMajorBump(bump, policy);
    }

    @VisibleForTesting
    protected Bump preMajorBump(final Bump bump, final VersionPolicy policy) {
        return switch (bump) {
            case MAJOR -> downgrade(Bump.MAJOR, Bump.MINOR, policy.bumpMinorPreMajor());
            case MINOR -> downgrade(Bump.MINOR, Bump.PATCH, policy.bumpPatchForMinorPreMajor());
            // No flag governs PATCH or NONE. Explicit default for the same reason as applyBump.
            default -> bump;
        };
    }

    @VisibleForTesting
    protected Bump downgrade(final Bump original, final Bump downgraded, final boolean enabled) {
        return enabled ? downgraded : original;
    }
}
