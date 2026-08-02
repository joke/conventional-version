package io.github.joke.conventionalversion.calc;

import static java.util.Comparator.naturalOrder;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Reduces a range of commits to the single bump it implies.
 *
 * <p>release-please has no list of releasable types: it renders the changelog and declines to release
 * when the body is empty, so releasability is really "does this type produce a visible changelog
 * section". The visible set is fixed for any repository that does not customise its changelog
 * sections, so it is encoded here as a table rather than reproduced as a second configuration
 * surface.
 */
public class BumpReducer {

    private static final String FEATURE_TYPE = "feat";

    /** The remaining types release-please renders visibly, all of which imply a patch release. */
    private static final Set<String> PATCH_TYPES = Set.of("fix", "perf", "revert", "deps");

    public Bump reduce(final List<ConventionalCommit> commits) {
        return commits.stream().map(this::impliedBump).max(naturalOrder()).orElse(Bump.NONE);
    }

    @VisibleForTesting
    protected Bump impliedBump(final ConventionalCommit commit) {
        return commit.findType()
                .map(type -> bumpForType(type, commit.breaking()))
                .orElse(Bump.NONE);
    }

    @VisibleForTesting
    protected Bump bumpForType(final String type, final boolean breaking) {
        if (breaking) {
            return Bump.MAJOR;
        }
        if (FEATURE_TYPE.equals(type)) {
            return Bump.MINOR;
        }
        if (PATCH_TYPES.contains(type)) {
            return Bump.PATCH;
        }
        return Bump.NONE;
    }
}
