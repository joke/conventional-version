package io.github.joke.conventionalversion;

import static java.util.Comparator.naturalOrder;

import io.github.joke.conventionalversion.VersionCatalogue.PackageVersion;
import io.github.joke.conventionalversion.calc.SemanticVersion;
import io.github.joke.conventionalversion.calc.VersionResult;
import io.github.joke.conventionalversion.config.LinkedGroup;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Raises every member of a linked group to the highest version any member calculated.
 *
 * <p>Runs after the per-package calculation, which is where {@code release-please} applies it: its
 * plugin reduces the group's candidates to the highest and then pushes a synthetic release for the
 * members that had none, so a member with no qualifying commits still releases.
 *
 * <p>This is the one propagation the calculation performs, and it is legitimate for the only reason
 * that matters: {@code release-please} does it. The Gradle project dependency graph gets no such
 * treatment, because release-please cannot see it.
 */
public class LinkedVersionReconciler {

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    /** Every package, with the members of each linked group levelled up to their highest version. */
    public List<PackageVersion> reconcile(final List<LinkedGroup> groups, final List<PackageVersion> calculated) {
        return calculated.stream()
                .map(version -> raiseIfGrouped(version, groups, calculated))
                .toList();
    }

    @VisibleForTesting
    protected PackageVersion raiseIfGrouped(
            final PackageVersion version, final List<LinkedGroup> groups, final List<PackageVersion> all) {
        return groupOf(version, groups)
                .map(group -> raise(version, membersOf(group, all)))
                .orElse(version);
    }

    @VisibleForTesting
    protected Optional<LinkedGroup> groupOf(final PackageVersion version, final List<LinkedGroup> groups) {
        return groups.stream()
                .filter(group -> group.holds(version.declared().component()))
                .findFirst();
    }

    @VisibleForTesting
    protected List<PackageVersion> membersOf(final LinkedGroup group, final List<PackageVersion> all) {
        return all.stream()
                .filter(candidate -> group.holds(candidate.declared().component()))
                .toList();
    }

    @VisibleForTesting
    protected PackageVersion raise(final PackageVersion version, final List<PackageVersion> members) {
        return highestOf(members)
                .map(highest -> new PackageVersion(
                        version.declared(), raised(version.result(), highest, anyReleasable(members))))
                .orElse(version);
    }

    @VisibleForTesting
    protected Optional<SemanticVersion> highestOf(final List<PackageVersion> members) {
        return members.stream()
                .map(member -> versionOf(member.result()))
                .flatMap(Optional::stream)
                .max(naturalOrder());
    }

    @VisibleForTesting
    protected boolean anyReleasable(final List<PackageVersion> members) {
        return members.stream().map(PackageVersion::result).anyMatch(VersionResult::releasable);
    }

    /** Total: a version that does not parse simply does not take part, rather than failing the build. */
    @VisibleForTesting
    protected Optional<SemanticVersion> versionOf(final VersionResult result) {
        return SemanticVersion.parse(result.version().replace(SNAPSHOT_SUFFIX, ""));
    }

    /**
     * A member already on its release commit stays bare, because the group releasing together does not
     * make an unreleased member released.
     */
    @VisibleForTesting
    protected VersionResult raised(
            final VersionResult member, final SemanticVersion highest, final boolean releasable) {
        final var snapshot = member.version().endsWith(SNAPSHOT_SUFFIX);
        final var version = snapshot ? highest + SNAPSHOT_SUFFIX : highest.toString();
        return new VersionResult(version, member.bump(), releasable, member.sha());
    }
}
