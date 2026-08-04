package io.github.joke.conventionalversion;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.joining;

import io.github.joke.conventionalversion.calc.Bump;
import io.github.joke.conventionalversion.calc.VersionResult;
import io.github.joke.conventionalversion.config.ReleasePackage;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Every package's version, and the answer for anything no package claims.
 *
 * <p>Computed once per build and then only read, so assigning a project's version is a lookup rather
 * than a calculation, and no project ever reaches into another.
 *
 * @param repositoryRoot the git root, which project directories are made relative to
 * @param packages each declared package with the version it resolved to
 * @param unmatched the result for a project no package claims
 */
public record VersionCatalogue(String repositoryRoot, List<PackageVersion> packages, VersionResult unmatched)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The version of a project that no package claims. Constant, so an unreleased module's output does
     * not change when an unrelated package releases, and never bare, because it is never a release.
     */
    public static final String UNRELEASED_VERSION = "0.0.0-SNAPSHOT";

    public VersionCatalogue {
        packages = List.copyOf(packages);
    }

    public static VersionResult unreleasable(final String sha) {
        return new VersionResult(UNRELEASED_VERSION, Bump.NONE, false, sha);
    }

    /** The version for a project directory, by the package whose claim is deepest. */
    public VersionResult forProject(final Path projectDirectory) {
        return forPath(relativise(projectDirectory));
    }

    /**
     * A project outside the git root cannot be claimed by any package, so it falls to the unreleasable
     * constant rather than being forced into a package it does not belong to.
     *
     * <p>The result is joined with {@code /} from the path's own elements rather than taken from its
     * {@code toString}, so a package path declared in release-please's configuration - which always
     * uses {@code /} - matches on every platform.
     */
    @VisibleForTesting
    String relativise(final Path projectDirectory) {
        final var root = Path.of(repositoryRoot).normalize();
        final var project = projectDirectory.normalize();
        if (!project.startsWith(root)) {
            return "";
        }
        return join(root.relativize(project));
    }

    @VisibleForTesting
    String join(final Path relative) {
        return IntStream.range(0, relative.getNameCount())
                .mapToObj(index -> relative.getName(index).toString())
                .collect(joining("/"));
    }

    @VisibleForTesting
    VersionResult forPath(final String relativePath) {
        return packages.stream()
                .filter(candidate -> candidate.declared().claims(relativePath))
                .max(comparingInt(candidate -> candidate.declared().depth()))
                .map(PackageVersion::result)
                .orElse(unmatched);
    }

    /**
     * One package and what it resolved to.
     *
     * @param declared the package as release-please declares it, carried so a project can be matched
     * @param result the version, bump and releasability of that package
     */
    public record PackageVersion(ReleasePackage declared, VersionResult result) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
