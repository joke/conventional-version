package io.github.joke.conventionalversion.config;

import static java.util.Comparator.comparingInt;

import java.util.List;
import java.util.Optional;

/**
 * The release layout of a repository: what is releasable, and what is held in lockstep.
 *
 * <p>A path claimed by no package is not meant to be released — an internal module, a shared
 * component, an aggregator. That is a statement the configuration makes, not an omission to repair.
 */
public record ReleaseConfiguration(List<ReleasePackage> packages, List<LinkedGroup> linkedGroups) {

    public ReleaseConfiguration {
        packages = List.copyOf(packages);
        linkedGroups = List.copyOf(linkedGroups);
    }

    /** The package claiming a repository-relative path, deepest declaration first. */
    public Optional<ReleasePackage> claiming(final String path) {
        return packages.stream().filter(candidate -> candidate.claims(path)).max(comparingInt(ReleasePackage::depth));
    }

    /** The group holding a component, when one does. */
    public Optional<LinkedGroup> groupOf(final String component) {
        return linkedGroups.stream().filter(group -> group.holds(component)).findFirst();
    }
}
